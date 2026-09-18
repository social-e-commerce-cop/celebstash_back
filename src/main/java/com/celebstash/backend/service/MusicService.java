package com.celebstash.backend.service;

import com.celebstash.backend.dto.music.*;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.ChatConversation;
import com.celebstash.backend.model.ChatParticipant;
import com.celebstash.backend.model.enums.ConversationType;
import com.celebstash.backend.model.enums.*;
import com.celebstash.backend.repository.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MusicService {

    private final MusicReleaseRepository releaseRepository;
    private final MusicTrackRepository trackRepository;
    private final MusicEntitlementRepository entitlementRepository;
    private final ReleaseBenefitRepository benefitRepository;
    private final MusicExclusiveContentRepository exclusiveContentRepository;
    private final MusicWaitlistRepository waitlistRepository;
    private final MusicAccessRepository accessRepository;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final NotificationService notificationService;
    private final ProductRepository productRepository;
    private final ConcertRepository concertRepository;
    private final ChatConversationRepository chatConversationRepository;
    private final ChatParticipantRepository chatParticipantRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<MusicRelease> getAllReleases() {
        return releaseRepository.findByStatusOrderByCreatedAtDesc("PUBLISHED");
    }

    public List<MusicRelease> getAllReleasesAdmin() {
        return releaseRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<MusicRelease> getReleaseById(Long id) {
        return releaseRepository.findById(id);
    }

    public Optional<MusicTrack> getTrackById(Long id) {
        return trackRepository.findById(id);
    }

    public List<MusicRelease> getArtistReleases(User artist, String status) {
        if (status != null && !status.trim().isEmpty()) {
            return releaseRepository.findByArtistAndStatusOrderByCreatedAtDesc(artist, status.trim().toUpperCase());
        }
        return releaseRepository.findByArtistOrderByCreatedAtDesc(artist);
    }

    @Transactional(readOnly = true)
    public ReleaseDetailResponse getReleaseDetail(Long releaseId, User currentUser) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        boolean isArtistOwner = currentUser != null && release.getArtist() != null &&
                release.getArtist().getId().equals(currentUser.getId());

        Optional<MusicAccess> accessOpt = (currentUser != null && !isArtistOwner)
                ? accessRepository.findByUserAndReleaseAndStatus(currentUser, release, MusicAccessStatus.ACTIVE)
                : Optional.empty();

        boolean hasAccess = isArtistOwner || accessOpt.isPresent();

        // Get configured benefits for release
        List<ReleaseBenefit> releaseBenefits = benefitRepository.findByRelease(release);
        List<ReleaseBenefitDto> benefitDtos = releaseBenefits.stream()
                .map(this::mapToBenefitDto)
                .collect(Collectors.toList());

        // Snapshot check for existing purchasers
        Set<BenefitType> activeBenefitTypes = new HashSet<>();
        if (isArtistOwner) {
            for (ReleaseBenefit b : releaseBenefits) {
                if (b.isEnabled()) activeBenefitTypes.add(b.getBenefitType());
            }
        } else if (accessOpt.isPresent()) {
            MusicAccess access = accessOpt.get();
            if (access.getGrantedBenefitsSnapshot() != null && !access.getGrantedBenefitsSnapshot().isEmpty()) {
                try {
                    List<String> bStrings = objectMapper.readValue(access.getGrantedBenefitsSnapshot(), new TypeReference<List<String>>() {});
                    for (String s : bStrings) {
                        try { activeBenefitTypes.add(BenefitType.valueOf(s)); } catch (Exception ignored) {}
                    }
                } catch (Exception e) {
                    for (ReleaseBenefit b : releaseBenefits) {
                        if (b.isEnabled()) activeBenefitTypes.add(b.getBenefitType());
                    }
                }
            } else {
                for (ReleaseBenefit b : releaseBenefits) {
                    if (b.isEnabled()) activeBenefitTypes.add(b.getBenefitType());
                }
            }
        }

        boolean canStreamFull = isArtistOwner || (hasAccess && activeBenefitTypes.contains(BenefitType.FULL_LISTENING));
        boolean canDownload = isArtistOwner || (hasAccess && activeBenefitTypes.contains(BenefitType.DOWNLOAD_ACCESS) && release.isDownloadAllowed());
        boolean hasCommunity = isArtistOwner || (hasAccess && activeBenefitTypes.contains(BenefitType.COMMUNITY_ACCESS));
        boolean hasExclusiveContent = isArtistOwner || (hasAccess && activeBenefitTypes.contains(BenefitType.EXCLUSIVE_CONTENT));

        boolean isWaitlisted = currentUser != null && waitlistRepository.existsByUserAndRelease(currentUser, release);
        long waitlistCount = waitlistRepository.countByRelease(release);

        // Fetch connected products
        List<Object> connectedProducts = new ArrayList<>();
        if (release.getConnectedProductIds() != null && !release.getConnectedProductIds().trim().isEmpty()) {
            String[] pIds = release.getConnectedProductIds().split(",");
            for (String pidStr : pIds) {
                try {
                    Long pid = Long.parseLong(pidStr.trim());
                    productRepository.findById(pid).ifPresent(connectedProducts::add);
                } catch (Exception ignored) {}
            }
        }

        // Fetch connected event
        Object connectedEvent = null;
        if (release.getConnectedEventId() != null) {
            connectedEvent = concertRepository.findById(release.getConnectedEventId()).orElse(null);
        }

        return ReleaseDetailResponse.builder()
                .release(release)
                .benefits(benefitDtos)
                .hasAccess(hasAccess)
                .isArtistOwner(isArtistOwner)
                .canStreamFull(canStreamFull)
                .canDownload(canDownload)
                .hasCommunity(hasCommunity)
                .hasExclusiveContent(hasExclusiveContent)
                .isWaitlisted(isWaitlisted)
                .waitlistCount(waitlistCount)
                .connectedProducts(connectedProducts)
                .connectedEvent(connectedEvent)
                .communityConversationId(release.getCommunityConversationId())
                .build();
    }

    @Transactional
    public MusicRelease createRelease(MusicRelease release, List<ReleaseBenefitDto> benefits, List<MusicExclusiveContentDto> exclusiveContents, User artist) {
        release.setArtist(artist);
        if (release.getStatus() == null) {
            release.setStatus("DRAFT");
        }

        // Auto-create community conversation if community benefit enabled
        boolean communityEnabled = benefits != null && benefits.stream().anyMatch(b -> b.getBenefitType() == BenefitType.COMMUNITY_ACCESS && b.isEnabled());
        if (communityEnabled && release.getCommunityConversationId() == null) {
            try {
                ChatConversation conv = ChatConversation.builder()
                        .type(ConversationType.GROUP)
                        .groupName(release.getTitle() + " Community")
                        .groupDescription("Official fan community for release: " + release.getTitle())
                        .groupAvatar(release.getCoverArtUrl())
                        .createdBy(artist)
                        .createdAt(LocalDateTime.now())
                        .build();
                conv = chatConversationRepository.save(conv);

                ChatParticipant p = ChatParticipant.builder()
                        .conversation(conv)
                        .user(artist)
                        .isAdmin(true)
                        .joinedAt(LocalDateTime.now())
                        .build();
                chatParticipantRepository.save(p);
                release.setCommunityConversationId(conv.getId());
            } catch (Exception e) {
                log.warn("Could not auto-create community chat conversation: {}", e.getMessage());
            }
        }

        MusicRelease saved = releaseRepository.save(release);

        // Save benefits
        if (benefits != null && !benefits.isEmpty()) {
            for (ReleaseBenefitDto bDto : benefits) {
                ReleaseBenefit b = ReleaseBenefit.builder()
                        .release(saved)
                        .benefitType(bDto.getBenefitType())
                        .enabled(bDto.isEnabled())
                        .customName(bDto.getCustomName())
                        .customDescription(bDto.getCustomDescription())
                        .configData(bDto.getConfigData())
                        .build();
                benefitRepository.save(b);
            }
        }

        // Save exclusive contents
        if (exclusiveContents != null && !exclusiveContents.isEmpty()) {
            for (int i = 0; i < exclusiveContents.size(); i++) {
                MusicExclusiveContentDto cDto = exclusiveContents.get(i);
                MusicExclusiveContent c = MusicExclusiveContent.builder()
                        .release(saved)
                        .title(cDto.getTitle())
                        .description(cDto.getDescription())
                        .contentType(cDto.getContentType() != null ? cDto.getContentType() : ExclusiveContentType.VIDEO)
                        .mediaUrl(cDto.getMediaUrl())
                        .thumbnailUrl(cDto.getThumbnailUrl())
                        .sortOrder(cDto.getSortOrder() != null ? cDto.getSortOrder() : i)
                        .createdAt(LocalDateTime.now())
                        .build();
                exclusiveContentRepository.save(c);
            }
        }

        return releaseRepository.findById(saved.getId()).orElse(saved);
    }

    @Transactional
    public MusicRelease updateRelease(Long releaseId, MusicRelease updated, List<ReleaseBenefitDto> benefits, List<MusicExclusiveContentDto> exclusiveContents, User artist) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        if (!release.getArtist().getId().equals(artist.getId()) && artist.getRole() != Role.ADMIN) {
            throw new AppException("You do not have permission to modify this release", HttpStatus.FORBIDDEN);
        }

        release.setTitle(updated.getTitle());
        release.setDescription(updated.getDescription());
        if (updated.getCoverArtUrl() != null) release.setCoverArtUrl(updated.getCoverArtUrl());
        if (updated.getReleaseType() != null) release.setReleaseType(updated.getReleaseType());
        if (updated.getGenre() != null) release.setGenre(updated.getGenre());
        if (updated.getSubgenre() != null) release.setSubgenre(updated.getSubgenre());
        if (updated.getAlbumPrice() != null) release.setAlbumPrice(updated.getAlbumPrice());
        if (updated.getReleaseDate() != null) release.setReleaseDate(updated.getReleaseDate());
        if (updated.getEarlyAccessDate() != null) release.setEarlyAccessDate(updated.getEarlyAccessDate());
        release.setDownloadAllowed(updated.isDownloadAllowed());
        if (updated.getConnectedEventId() != null) release.setConnectedEventId(updated.getConnectedEventId());
        if (updated.getConnectedProductIds() != null) release.setConnectedProductIds(updated.getConnectedProductIds());
        if (updated.getReleaseStory() != null) release.setReleaseStory(updated.getReleaseStory());
        if (updated.getCredits() != null) release.setCredits(updated.getCredits());
        if (updated.getCopyrightInfo() != null) release.setCopyrightInfo(updated.getCopyrightInfo());
        if (updated.getAvailabilityStatus() != null) release.setAvailabilityStatus(updated.getAvailabilityStatus());

        // Update benefits (without affecting snapshots of existing purchasers)
        if (benefits != null) {
            release.getBenefits().clear();
            for (ReleaseBenefitDto bDto : benefits) {
                ReleaseBenefit b = ReleaseBenefit.builder()
                        .release(release)
                        .benefitType(bDto.getBenefitType())
                        .enabled(bDto.isEnabled())
                        .customName(bDto.getCustomName())
                        .customDescription(bDto.getCustomDescription())
                        .configData(bDto.getConfigData())
                        .build();
                release.getBenefits().add(b);
            }
        }

        // Update exclusive contents
        if (exclusiveContents != null) {
            release.getExclusiveContents().clear();
            for (int i = 0; i < exclusiveContents.size(); i++) {
                MusicExclusiveContentDto cDto = exclusiveContents.get(i);
                MusicExclusiveContent c = MusicExclusiveContent.builder()
                        .release(release)
                        .title(cDto.getTitle())
                        .description(cDto.getDescription())
                        .contentType(cDto.getContentType() != null ? cDto.getContentType() : ExclusiveContentType.VIDEO)
                        .mediaUrl(cDto.getMediaUrl())
                        .thumbnailUrl(cDto.getThumbnailUrl())
                        .sortOrder(cDto.getSortOrder() != null ? cDto.getSortOrder() : i)
                        .createdAt(LocalDateTime.now())
                        .build();
                release.getExclusiveContents().add(c);
            }
        }

        return releaseRepository.save(release);
    }

    @Transactional
    public MusicRelease publishRelease(Long releaseId, User artist) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        if (!release.getArtist().getId().equals(artist.getId()) && artist.getRole() != Role.ADMIN) {
            throw new AppException("You do not have permission to publish this release", HttpStatus.FORBIDDEN);
        }

        release.setStatus("PUBLISHED");
        if (release.getAvailabilityStatus() == null || release.getAvailabilityStatus().equalsIgnoreCase("DRAFT")) {
            release.setAvailabilityStatus("PUBLICLY_RELEASED");
        }
        MusicRelease saved = releaseRepository.save(release);

        // Notify waitlist members
        List<MusicWaitlist> waitlist = waitlistRepository.findByRelease(release);
        for (MusicWaitlist w : waitlist) {
            if (!w.isNotified()) {
                notificationService.createNotification(
                        w.getUser(),
                        "Release Available!",
                        "'" + release.getTitle() + "' by " + artist.getFullName() + " is now available to unlock!",
                        NotificationType.NEW_MUSIC_FROM_FOLLOWED_ARTIST,
                        release.getId()
                );
                w.setNotified(true);
                waitlistRepository.save(w);
            }
        }

        return saved;
    }

    @Transactional
    public MusicAccess purchaseReleaseAccess(User buyer, PurchaseAccessDto req) {
        MusicRelease release = releaseRepository.findById(req.getReleaseId())
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        // Determine target recipient (fan receiving access)
        User targetFan = buyer;
        if (req.isGift()) {
            if (req.getGiftRecipientId() != null) {
                targetFan = userRepository.findById(req.getGiftRecipientId())
                        .orElseThrow(() -> new AppException("Gift recipient user not found", HttpStatus.NOT_FOUND));
            } else if (req.getGiftRecipientUsername() != null && !req.getGiftRecipientUsername().trim().isEmpty()) {
                targetFan = userRepository.findByUsername(req.getGiftRecipientUsername().trim())
                        .orElseThrow(() -> new AppException("Gift recipient @" + req.getGiftRecipientUsername() + " not found", HttpStatus.NOT_FOUND));
            } else {
                throw new AppException("Gift recipient is required when giving as gift", HttpStatus.BAD_REQUEST);
            }
        }

        // Idempotency: Prevent duplicate purchases
        if (accessRepository.existsByUserAndReleaseAndStatus(targetFan, release, MusicAccessStatus.ACTIVE)) {
            throw new AppException((req.isGift() ? "Recipient" : "You") + " already have Access to this release", HttpStatus.CONFLICT);
        }

        BigDecimal price = release.getAlbumPrice() != null ? release.getAlbumPrice() : BigDecimal.valueOf(9.99);

        // Wallet payment flow
        Wallet buyerWallet = walletRepository.findByUser(buyer)
                .orElseGet(() -> walletRepository.save(Wallet.builder().user(buyer).balance(BigDecimal.ZERO).createdAt(LocalDateTime.now()).build()));

        // Validate PIN if set
        if (buyerWallet.getPin() != null && !buyerWallet.getPin().isEmpty()) {
            if (req.getPin() == null || !buyerWallet.getPin().equals(req.getPin())) {
                throw new AppException("Invalid wallet PIN", HttpStatus.BAD_REQUEST);
            }
        }

        // Balance check
        if (buyerWallet.getBalance().compareTo(price) < 0) {
            throw new AppException("Insufficient wallet balance for this purchase ($" + price + " required)", HttpStatus.BAD_REQUEST);
        }

        // Deduct buyer funds
        buyerWallet.setBalance(buyerWallet.getBalance().subtract(price));
        buyerWallet.setUpdatedAt(LocalDateTime.now());
        walletRepository.save(buyerWallet);

        // Record buyer transaction
        String txDesc = "Music Access: " + release.getTitle() + (req.isGift() ? " (Gift for @" + targetFan.getUsername() + ")" : "");
        Transaction buyerTx = Transaction.builder()
                .wallet(buyerWallet)
                .amount(price)
                .type(TransactionType.PURCHASE)
                .status(TransactionStatus.COMPLETED)
                .description(txDesc)
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        buyerTx = transactionRepository.save(buyerTx);

        // Artist Payout (95% after 5% platform fee)
        User artist = release.getArtist();
        if (artist != null) {
            BigDecimal platformFee = price.multiply(new BigDecimal("0.05"));
            BigDecimal artistPayout = price.subtract(platformFee);

            Wallet artistWallet = walletRepository.findByUser(artist)
                    .orElseGet(() -> walletRepository.save(Wallet.builder().user(artist).balance(BigDecimal.ZERO).createdAt(LocalDateTime.now()).build()));

            artistWallet.setBalance(artistWallet.getBalance().add(artistPayout));
            artistWallet.setUpdatedAt(LocalDateTime.now());
            walletRepository.save(artistWallet);

            Transaction artistTx = Transaction.builder()
                    .wallet(artistWallet)
                    .amount(artistPayout)
                    .type(TransactionType.DEPOSIT)
                    .status(TransactionStatus.COMPLETED)
                    .description("Music Access sale payout for '" + release.getTitle() + "' (minus 5% fee)")
                    .createdAt(LocalDateTime.now())
                    .completedAt(LocalDateTime.now())
                    .build();
            transactionRepository.save(artistTx);

            // Notify artist of sale
            notificationService.createNotification(
                    artist,
                    "New Music Access Sale!",
                    "@" + buyer.getUsername() + " purchased Access to '" + release.getTitle() + "'. Payout: $" + artistPayout,
                    NotificationType.MUSIC_UNLOCKED,
                    release.getId()
            );
        }

        // Create permanent snapshot of active benefits
        List<ReleaseBenefit> releaseBenefits = benefitRepository.findByReleaseAndEnabledTrue(release);
        List<String> enabledTypes = releaseBenefits.stream()
                .map(b -> b.getBenefitType().name())
                .collect(Collectors.toList());
        String snapshotJson = "[]";
        try {
            snapshotJson = objectMapper.writeValueAsString(enabledTypes);
        } catch (Exception ignored) {}

        // Create real MusicAccess record
        MusicAccess access = MusicAccess.builder()
                .user(targetFan)
                .release(release)
                .transaction(buyerTx)
                .status(MusicAccessStatus.ACTIVE)
                .grantedAt(LocalDateTime.now())
                .isGift(req.isGift())
                .giftedBy(req.isGift() ? buyer : null)
                .giftMessage(req.getGiftMessage())
                .grantedBenefitsSnapshot(snapshotJson)
                .build();
        MusicAccess savedAccess = accessRepository.save(access);

        // Maintain MusicEntitlement for playback engine backwards-compatibility
        final User finalTargetFan = targetFan;
        entitlementRepository.findByUserAndReleaseAndTrackIsNull(finalTargetFan, release).orElseGet(() -> {
            MusicEntitlement ent = MusicEntitlement.builder()
                    .user(finalTargetFan)
                    .release(release)
                    .accessType(AccessPackageType.PERMANENT_STREAMING)
                    .playsGranted(999999)
                    .playsUsed(0)
                    .playsRemaining(999999)
                    .isPermanent(true)
                    .amountPaid(price)
                    .purchasedAt(LocalDateTime.now())
                    .build();
            return entitlementRepository.save(ent);
        });

        // Add to community chat if community access enabled
        if (enabledTypes.contains(BenefitType.COMMUNITY_ACCESS.name()) && release.getCommunityConversationId() != null) {
            try {
                ChatConversation conv = chatConversationRepository.findById(release.getCommunityConversationId()).orElse(null);
                if (conv != null && !chatParticipantRepository.existsByConversationAndUser(conv, targetFan)) {
                    ChatParticipant p = ChatParticipant.builder()
                            .conversation(conv)
                            .user(targetFan)
                            .isAdmin(false)
                            .joinedAt(LocalDateTime.now())
                            .build();
                    chatParticipantRepository.save(p);
                }
            } catch (Exception ex) {
                log.warn("Could not add fan to community chat: {}", ex.getMessage());
            }
        }

        // Send fan notifications
        if (req.isGift()) {
            notificationService.createNotification(
                    targetFan,
                    "You Received a Gift!",
                    "@" + buyer.getUsername() + " gifted you Access to '" + release.getTitle() + "'!" +
                            (req.getGiftMessage() != null ? " Message: \"" + req.getGiftMessage() + "\"" : ""),
                    NotificationType.MUSIC_UNLOCKED,
                    release.getId()
            );
            notificationService.createNotification(
                    buyer,
                    "Gift Sent Successfully!",
                    "You gifted Access to '" + release.getTitle() + "' to @" + targetFan.getUsername(),
                    NotificationType.PURCHASE_SUCCESSFUL,
                    release.getId()
            );
        } else {
            notificationService.createNotification(
                    buyer,
                    "Access Unlocked!",
                    "You now have Access to '" + release.getTitle() + "' with configured artist benefits.",
                    NotificationType.PURCHASE_SUCCESSFUL,
                    release.getId()
            );
        }

        return savedAccess;
    }

    @Transactional
    public MusicWaitlist joinWaitlist(User user, Long releaseId) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        if (waitlistRepository.existsByUserAndRelease(user, release)) {
            throw new AppException("You are already on the waitlist for this release", HttpStatus.CONFLICT);
        }

        MusicWaitlist waitlist = MusicWaitlist.builder()
                .user(user)
                .release(release)
                .notified(false)
                .createdAt(LocalDateTime.now())
                .build();
        return waitlistRepository.save(waitlist);
    }

    public List<MusicRelease> getUserPurchasedReleases(User user) {
        List<MusicAccess> accesses = accessRepository.findByUserAndStatusOrderByGrantedAtDesc(user, MusicAccessStatus.ACTIVE);
        return accesses.stream()
                .map(MusicAccess::getRelease)
                .distinct()
                .collect(Collectors.toList());
    }

    public Map<String, Object> verifyPlaybackAccess(User user, Long trackId) {
        Map<String, Object> response = new HashMap<>();
        response.put("hasFullAccess", false);
        response.put("remainingPlays", 0);
        response.put("isPermanent", false);
        response.put("maxDurationSeconds", 5);

        if (user == null || trackId == null) {
            return response;
        }

        MusicTrack track = trackRepository.findById(trackId).orElse(null);
        if (track == null) return response;

        MusicRelease release = track.getRelease();
        if (release != null && release.getArtist() != null && release.getArtist().getId().equals(user.getId())) {
            response.put("hasFullAccess", true);
            response.put("remainingPlays", 999999);
            response.put("isPermanent", true);
            response.put("maxDurationSeconds", null);
            return response;
        }

        if (release != null) {
            Optional<MusicAccess> accessOpt = accessRepository.findByUserAndReleaseAndStatus(user, release, MusicAccessStatus.ACTIVE);
            if (accessOpt.isPresent()) {
                MusicAccess access = accessOpt.get();
                // Check if FULL_LISTENING is in granted snapshot
                boolean fullListeningEnabled = false;
                if (access.getGrantedBenefitsSnapshot() != null) {
                    fullListeningEnabled = access.getGrantedBenefitsSnapshot().contains(BenefitType.FULL_LISTENING.name());
                } else {
                    fullListeningEnabled = benefitRepository.findByReleaseAndBenefitType(release, BenefitType.FULL_LISTENING)
                            .map(ReleaseBenefit::isEnabled).orElse(true);
                }

                if (fullListeningEnabled) {
                    response.put("hasFullAccess", true);
                    response.put("remainingPlays", 999999);
                    response.put("isPermanent", true);
                    response.put("maxDurationSeconds", null);
                    return response;
                }
            }
        }

        return response;
    }

    public boolean canDownloadTrack(User user, Long trackId) {
        if (user == null || trackId == null) return false;
        MusicTrack track = trackRepository.findById(trackId).orElse(null);
        if (track == null || !track.isAllowDownload()) return false;

        MusicRelease release = track.getRelease();
        if (release == null || !release.isDownloadAllowed()) return false;

        if (release.getArtist() != null && release.getArtist().getId().equals(user.getId())) {
            return true;
        }

        Optional<MusicAccess> accessOpt = accessRepository.findByUserAndReleaseAndStatus(user, release, MusicAccessStatus.ACTIVE);
        if (accessOpt.isPresent()) {
            MusicAccess access = accessOpt.get();
            if (access.getGrantedBenefitsSnapshot() != null) {
                return access.getGrantedBenefitsSnapshot().contains(BenefitType.DOWNLOAD_ACCESS.name());
            }
            return benefitRepository.findByReleaseAndBenefitType(release, BenefitType.DOWNLOAD_ACCESS)
                    .map(ReleaseBenefit::isEnabled).orElse(false);
        }

        return false;
    }

    public List<MusicExclusiveContentDto> getExclusiveContents(User user, Long releaseId) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        boolean isArtist = user != null && release.getArtist() != null && release.getArtist().getId().equals(user.getId());
        boolean hasExclusive = false;

        if (isArtist) {
            hasExclusive = true;
        } else if (user != null) {
            Optional<MusicAccess> accessOpt = accessRepository.findByUserAndReleaseAndStatus(user, release, MusicAccessStatus.ACTIVE);
            if (accessOpt.isPresent()) {
                MusicAccess access = accessOpt.get();
                if (access.getGrantedBenefitsSnapshot() != null) {
                    hasExclusive = access.getGrantedBenefitsSnapshot().contains(BenefitType.EXCLUSIVE_CONTENT.name());
                } else {
                    hasExclusive = benefitRepository.findByReleaseAndBenefitType(release, BenefitType.EXCLUSIVE_CONTENT)
                            .map(ReleaseBenefit::isEnabled).orElse(false);
                }
            }
        }

        if (!hasExclusive) {
            throw new AppException("You must hold Access with Exclusive Content enabled to view this content", HttpStatus.FORBIDDEN);
        }

        return exclusiveContentRepository.findByReleaseOrderBySortOrderAscCreatedAtAsc(release).stream()
                .map(this::mapToExclusiveContentDto)
                .collect(Collectors.toList());
    }

    public ArtistStudioStatsDto getArtistStudioStats(User artist) {
        List<MusicRelease> allReleases = releaseRepository.findByArtistOrderByCreatedAtDesc(artist);
        long total = allReleases.size();
        long drafts = allReleases.stream().filter(r -> "DRAFT".equalsIgnoreCase(r.getStatus())).count();
        long published = allReleases.stream().filter(r -> "PUBLISHED".equalsIgnoreCase(r.getStatus())).count();

        List<MusicAccess> artistAccesses = accessRepository.findByArtistReleases(artist);
        long totalOrders = artistAccesses.size();

        BigDecimal totalRevenue = BigDecimal.ZERO;
        for (MusicAccess a : artistAccesses) {
            if (a.getRelease() != null && a.getRelease().getAlbumPrice() != null) {
                totalRevenue = totalRevenue.add(a.getRelease().getAlbumPrice());
            }
        }

        BigDecimal artistPayouts = totalRevenue.multiply(new BigDecimal("0.95"));
        long totalFans = artistAccesses.stream().map(a -> a.getUser().getId()).distinct().count();

        List<Object> recentSales = artistAccesses.stream().limit(10).map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("accessId", a.getId());
            m.put("releaseTitle", a.getRelease() != null ? a.getRelease().getTitle() : "Unknown");
            m.put("buyerUsername", a.getUser() != null ? a.getUser().getUsername() : "fan");
            m.put("grantedAt", a.getGrantedAt());
            m.put("price", a.getRelease() != null ? a.getRelease().getAlbumPrice() : BigDecimal.ZERO);
            m.put("isGift", a.isGift());
            return m;
        }).collect(Collectors.toList());

        return ArtistStudioStatsDto.builder()
                .totalReleases(total)
                .draftReleases(drafts)
                .publishedReleases(published)
                .totalOrders(totalOrders)
                .totalRevenue(totalRevenue)
                .artistPayouts(artistPayouts)
                .totalFans(totalFans)
                .recentSales(recentSales)
                .build();
    }

    private ReleaseBenefitDto mapToBenefitDto(ReleaseBenefit b) {
        return ReleaseBenefitDto.builder()
                .id(b.getId())
                .benefitType(b.getBenefitType())
                .enabled(b.isEnabled())
                .customName(b.getCustomName())
                .customDescription(b.getCustomDescription())
                .configData(b.getConfigData())
                .build();
    }

    private MusicExclusiveContentDto mapToExclusiveContentDto(MusicExclusiveContent c) {
        return MusicExclusiveContentDto.builder()
                .id(c.getId())
                .title(c.getTitle())
                .description(c.getDescription())
                .contentType(c.getContentType())
                .mediaUrl(c.getMediaUrl())
                .thumbnailUrl(c.getThumbnailUrl())
                .sortOrder(c.getSortOrder())
                .createdAt(c.getCreatedAt())
                .build();
    }

    @Transactional
    public MusicTrack addTrackToRelease(MusicRelease release, MusicTrack track) {
        track.setRelease(release);
        return trackRepository.save(track);
    }

    public List<MusicEntitlement> getUserEntitlements(User user) {
        return entitlementRepository.findByUserOrderByPurchasedAtDesc(user);
    }
}
