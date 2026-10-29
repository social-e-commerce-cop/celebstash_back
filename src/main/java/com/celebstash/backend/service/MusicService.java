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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final MusicPlayRepository musicPlayRepository;
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
        return releaseRepository.findByStatusOrderByCreatedAtDesc(MusicRelease.STATUS_PUBLISHED);
    }

    public List<MusicRelease> getAllReleasesAdmin() {
        return releaseRepository.findAllByOrderByCreatedAtDesc();
    }

    // ---------------------------------------------------------------------------------------------
    // Visibility rules. Enforced here, not in the clients: anything that is not PUBLISHED is
    // invisible to ordinary users, except an ARCHIVED release its existing Access holders keep.

    private boolean isAdmin(User user) {
        return user != null && user.getRole() == Role.ADMIN;
    }

    private boolean isOwner(MusicRelease release, User user) {
        return user != null && release.getArtist() != null && release.getArtist().getId().equals(user.getId());
    }

    private boolean hasActiveAccess(MusicRelease release, User user) {
        return user != null && accessRepository.existsByUserAndReleaseAndStatus(user, release, MusicAccessStatus.ACTIVE);
    }

    public boolean canView(MusicRelease release, User user) {
        String status = release.getStatus();
        if (MusicRelease.STATUS_PUBLISHED.equalsIgnoreCase(status)) {
            return true;
        }
        if (isOwner(release, user) || isAdmin(user)) {
            return true;
        }
        return MusicRelease.STATUS_ARCHIVED.equalsIgnoreCase(status) && hasActiveAccess(release, user);
    }

    private MusicRelease getVisibleRelease(Long releaseId, User user) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));
        if (!canView(release, user)) {
            // 404 rather than 403 so unpublished releases cannot be probed for.
            throw new AppException("Release not found", HttpStatus.NOT_FOUND);
        }
        return release;
    }

    /** The track, if its release is visible to {@code user}; otherwise 404. */
    public MusicTrack getVisibleTrack(Long trackId, User user) {
        MusicTrack track = trackRepository.findById(trackId)
                .orElseThrow(() -> new AppException("Track not found", HttpStatus.NOT_FOUND));
        if (track.getRelease() == null || !canView(track.getRelease(), user)) {
            throw new AppException("Track not found", HttpStatus.NOT_FOUND);
        }
        return track;
    }

    // Small LRU cache of generated previews: cutting one means loading the full audio blob.
    private static final int PREVIEW_CACHE_SIZE = 64;
    private final Map<Long, byte[]> previewCache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, byte[]> eldest) {
                    return size() > PREVIEW_CACHE_SIZE;
                }
            });

    /** Full-length audio bytes. Callers must have checked entitlement first. */
    public byte[] loadFullAudio(MusicTrack track) {
        return fileStorageService.loadFileBytes(track.getAudioFileUrl());
    }

    /** A server-cut preview containing only the track's preview window (5 seconds by default). */
    public byte[] loadPreviewAudio(MusicTrack track) {
        return previewCache.computeIfAbsent(track.getId(), id -> {
            int seconds = track.getPreviewDurationSeconds() != null && track.getPreviewDurationSeconds() > 0
                    ? track.getPreviewDurationSeconds() : DEFAULT_PREVIEW_SECONDS;
            try {
                return com.celebstash.backend.util.AudioPreviewUtil.trimToSeconds(loadFullAudio(track), seconds);
            } catch (IllegalArgumentException ex) {
                throw new AppException("A preview is not available for this track.", HttpStatus.UNPROCESSABLE_ENTITY);
            }
        });
    }

    public static final int DEFAULT_PREVIEW_SECONDS = 5;

    // ---------------------------------------------------------------------------------------------
    // Admin review workflow. Approval applies to the whole release (single, EP, album or playlist)
    // and all of its tracks, because tracks have no publication state of their own.

    public List<MusicRelease> getReleasesForReview(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) {
            // Drafts have not been submitted, so they are not the administrator's concern.
            return releaseRepository.findByStatusInOrderByCreatedAtDesc(List.of(
                    MusicRelease.STATUS_PENDING_REVIEW, MusicRelease.STATUS_PUBLISHED,
                    MusicRelease.STATUS_REJECTED, MusicRelease.STATUS_ARCHIVED));
        }
        return releaseRepository.findByStatusOrderByCreatedAtDesc(status.trim().toUpperCase());
    }

    public MusicRelease getReleaseForReview(Long releaseId) {
        return releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public MusicRelease approveRelease(Long releaseId, User admin) {
        if (!isAdmin(admin)) {
            throw new AppException("Only administrators can approve music", HttpStatus.FORBIDDEN);
        }
        MusicRelease release = getReleaseForReview(releaseId);
        if (!MusicRelease.STATUS_PENDING_REVIEW.equalsIgnoreCase(release.getStatus())
                && !MusicRelease.STATUS_REJECTED.equalsIgnoreCase(release.getStatus())) {
            throw new AppException("Only releases awaiting review can be approved (current status: " + release.getStatus() + ")", HttpStatus.CONFLICT);
        }
        if (release.getTracks() == null || release.getTracks().isEmpty()) {
            throw new AppException("A release without tracks cannot be approved", HttpStatus.BAD_REQUEST);
        }

        LocalDateTime now = LocalDateTime.now();
        release.setStatus(MusicRelease.STATUS_PUBLISHED);
        release.setReviewedAt(now);
        release.setRejectionReason(null);
        if (release.getApprovedAt() == null) {
            release.setApprovedAt(now);
        }
        if (release.getPublicReleaseDate() == null) {
            release.setPublicReleaseDate(now);
        }
        if (release.getAvailabilityStatus() == null || release.getAvailabilityStatus().equalsIgnoreCase("DRAFT")) {
            release.setAvailabilityStatus("PUBLICLY_RELEASED");
        }
        MusicRelease saved = releaseRepository.save(release);

        notificationService.createNotification(
                release.getArtist(),
                "Release Approved",
                "'" + release.getTitle() + "' was approved and is now live for fans.",
                NotificationType.MUSIC_RELEASE_APPROVED,
                release.getId());
        notifyWaitlist(release);
        return saved;
    }

    @Transactional
    public MusicRelease rejectRelease(Long releaseId, User admin, String reason) {
        if (!isAdmin(admin)) {
            throw new AppException("Only administrators can reject music", HttpStatus.FORBIDDEN);
        }
        if (reason == null || reason.trim().isEmpty()) {
            throw new AppException("A rejection reason is required", HttpStatus.BAD_REQUEST);
        }
        MusicRelease release = getReleaseForReview(releaseId);
        if (!MusicRelease.STATUS_PENDING_REVIEW.equalsIgnoreCase(release.getStatus())) {
            throw new AppException("Only releases awaiting review can be rejected (current status: " + release.getStatus() + ")", HttpStatus.CONFLICT);
        }

        String trimmed = reason.trim();
        release.setStatus(MusicRelease.STATUS_REJECTED);
        release.setReviewedAt(LocalDateTime.now());
        release.setRejectionReason(trimmed.length() > 1000 ? trimmed.substring(0, 1000) : trimmed);
        MusicRelease saved = releaseRepository.save(release);

        notificationService.createNotification(
                release.getArtist(),
                "Release Not Approved",
                "'" + release.getTitle() + "' was not approved. Reason: " + release.getRejectionReason(),
                NotificationType.MUSIC_RELEASE_REJECTED,
                release.getId());
        return saved;
    }

    private void notifyWaitlist(MusicRelease release) {
        String artistName = release.getArtist() != null ? release.getArtist().getFullName() : "the artist";
        for (MusicWaitlist w : waitlistRepository.findByRelease(release)) {
            if (!w.isNotified()) {
                notificationService.createNotification(
                        w.getUser(),
                        "Release Available!",
                        "'" + release.getTitle() + "' by " + artistName + " is now available to unlock!",
                        NotificationType.NEW_MUSIC_FROM_FOLLOWED_ARTIST,
                        release.getId()
                );
                w.setNotified(true);
                waitlistRepository.save(w);
            }
        }
    }

    /** Maps the status an uploader asked for onto the lifecycle; only admins may publish directly. */
    public String resolveInitialStatus(String requestedStatus, User uploader) {
        String requested = requestedStatus == null ? "" : requestedStatus.trim().toUpperCase();
        if (MusicRelease.STATUS_DRAFT.equals(requested)) {
            return MusicRelease.STATUS_DRAFT;
        }
        if (isAdmin(uploader) && MusicRelease.STATUS_PUBLISHED.equals(requested)) {
            return MusicRelease.STATUS_PUBLISHED;
        }
        return MusicRelease.STATUS_PENDING_REVIEW;
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
        MusicRelease release = getVisibleRelease(releaseId, currentUser);

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

        Long totalAccessCount = null;
        BigDecimal totalRevenue = null;
        List<Map<String, Object>> accessHolders = null;
        Long totalPlays = null;

        if (isArtistOwner) {
            List<MusicAccess> releaseAccesses = accessRepository.findByReleaseAndStatusOrderByGrantedAtDesc(release, MusicAccessStatus.ACTIVE);
            totalAccessCount = (long) releaseAccesses.size();
            BigDecimal price = release.getAlbumPrice() != null ? release.getAlbumPrice() : BigDecimal.ZERO;
            totalRevenue = price.multiply(BigDecimal.valueOf(totalAccessCount));
            int trackCount = (release.getTracks() != null && !release.getTracks().isEmpty()) ? release.getTracks().size() : 1;
            totalPlays = totalAccessCount * 4L + (long) (trackCount * 6L);

            accessHolders = releaseAccesses.stream().limit(50).map(a -> {
                Map<String, Object> m = new HashMap<>();
                m.put("id", a.getId());
                m.put("userId", a.getUser() != null ? a.getUser().getId() : null);
                m.put("username", a.getUser() != null ? a.getUser().getUsername() : "fan");
                m.put("fullName", a.getUser() != null ? a.getUser().getFullName() : null);
                m.put("profilePicture", a.getUser() != null ? a.getUser().getProfilePicture() : null);
                m.put("grantedAt", a.getGrantedAt());
                m.put("isGift", a.isGift());
                m.put("giftedBy", a.getGiftedBy() != null ? a.getGiftedBy().getUsername() : null);
                return m;
            }).collect(Collectors.toList());
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
                .totalAccessCount(totalAccessCount)
                .totalRevenue(totalRevenue)
                .accessHolders(accessHolders)
                .totalPlays(totalPlays)
                .build();
    }

    @Transactional
    public MusicRelease createRelease(MusicRelease release, List<ReleaseBenefitDto> benefits, List<MusicExclusiveContentDto> exclusiveContents, User artist) {
        // The artist always comes from the authenticated principal, never from the request.
        release.setArtist(artist);
        release.setStatus(resolveInitialStatus(release.getStatus(), artist));
        LocalDateTime now = LocalDateTime.now();
        if (MusicRelease.STATUS_PENDING_REVIEW.equals(release.getStatus())) {
            release.setSubmittedAt(now);
        } else if (MusicRelease.STATUS_PUBLISHED.equals(release.getStatus())) {
            release.setSubmittedAt(now);
            release.setReviewedAt(now);
            release.setApprovedAt(now);
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
        if (updated.getAlbumPrice() != null) {
            if (updated.getAlbumPrice().compareTo(BigDecimal.ZERO) < 0) {
                throw new AppException("Price cannot be negative", HttpStatus.BAD_REQUEST);
            }
            release.setAlbumPrice(updated.getAlbumPrice());
        }
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

    /**
     * Submits a draft (or a rejected release the artist has revised) for administrator review.
     * Publishing itself happens only through {@link #approveRelease}; an administrator calling this
     * endpoint approves the release directly.
     */
    @Transactional
    public MusicRelease publishRelease(Long releaseId, User artist) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        if (isAdmin(artist)) {
            return approveRelease(releaseId, artist);
        }
        if (!isOwner(release, artist)) {
            throw new AppException("You do not have permission to submit this release", HttpStatus.FORBIDDEN);
        }

        String status = release.getStatus() == null ? "" : release.getStatus().toUpperCase();
        if (MusicRelease.STATUS_PENDING_REVIEW.equals(status) || MusicRelease.STATUS_PUBLISHED.equals(status)) {
            return release;
        }
        if (!MusicRelease.STATUS_DRAFT.equals(status) && !MusicRelease.STATUS_REJECTED.equals(status)) {
            throw new AppException("This release cannot be submitted for review from status " + status, HttpStatus.CONFLICT);
        }
        if (release.getTracks() == null || release.getTracks().isEmpty()) {
            throw new AppException("Add at least one track before submitting for review", HttpStatus.BAD_REQUEST);
        }

        release.setStatus(MusicRelease.STATUS_PENDING_REVIEW);
        release.setSubmittedAt(LocalDateTime.now());
        return releaseRepository.save(release);
    }

    @Transactional
    public MusicAccess purchaseReleaseAccess(User buyer, PurchaseAccessDto req) {
        MusicRelease release = releaseRepository.findById(req.getReleaseId())
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        // Only approved, live releases are for sale. Pending, rejected, draft and archived ones are not.
        if (!MusicRelease.STATUS_PUBLISHED.equalsIgnoreCase(release.getStatus())) {
            throw new AppException("This release is not available for purchase", HttpStatus.NOT_FOUND);
        }

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

        if (isOwner(release, targetFan)) {
            throw new AppException("The artist already owns this release", HttpStatus.CONFLICT);
        }

        // The price always comes from the database; any price the client sends is ignored.
        BigDecimal price = release.getAlbumPrice() != null ? release.getAlbumPrice() : BigDecimal.valueOf(9.99);

        // Wallet payment flow. The wallet row is locked for the rest of the transaction so two
        // concurrent purchases (a double tap, a retried request) cannot both pass the checks below.
        Wallet unlockedWallet = walletRepository.findByUser(buyer)
                .orElseGet(() -> walletRepository.save(Wallet.builder().user(buyer).balance(BigDecimal.ZERO).createdAt(LocalDateTime.now()).build()));
        Wallet buyerWallet = walletRepository.findByIdForUpdate(unlockedWallet.getId()).orElse(unlockedWallet);

        // Idempotency: Prevent duplicate purchases (checked after taking the lock)
        if (accessRepository.existsByUserAndReleaseAndStatus(targetFan, release, MusicAccessStatus.ACTIVE)) {
            throw new AppException((req.isGift() ? "Recipient" : "You") + " already have Access to this release", HttpStatus.CONFLICT);
        }

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
        MusicRelease release = getVisibleRelease(releaseId, user);
        if (!MusicRelease.STATUS_PUBLISHED.equalsIgnoreCase(release.getStatus())) {
            throw new AppException("This release is not open for a waitlist", HttpStatus.BAD_REQUEST);
        }

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
        if (release == null || !canView(release, user)) return response;

        // Owners hear their own work; administrators review submissions in full.
        if (isOwner(release, user) || isAdmin(user)) {
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
        if (release == null || !release.isDownloadAllowed() || !canView(release, user)) return false;

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
        MusicRelease release = getVisibleRelease(releaseId, user);

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
        long drafts = allReleases.stream().filter(r -> MusicRelease.STATUS_DRAFT.equalsIgnoreCase(r.getStatus())).count();
        long published = allReleases.stream().filter(r -> MusicRelease.STATUS_PUBLISHED.equalsIgnoreCase(r.getStatus())).count();
        long pending = allReleases.stream().filter(r -> MusicRelease.STATUS_PENDING_REVIEW.equalsIgnoreCase(r.getStatus())).count();
        long rejected = allReleases.stream().filter(r -> MusicRelease.STATUS_REJECTED.equalsIgnoreCase(r.getStatus())).count();

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
                .pendingReleases(pending)
                .rejectedReleases(rejected)
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

    @Transactional
    public MusicPlay recordPlay(Long trackId, User user) {
        MusicTrack track = getVisibleTrack(trackId, user);
        MusicRelease release = track.getRelease();

        MusicPlay play = MusicPlay.builder()
                .release(release)
                .track(track)
                .user(user)
                .playedAt(LocalDateTime.now())
                .durationSeconds(track.getDurationSeconds())
                .build();

        return musicPlayRepository.save(play);
    }

    @Transactional(readOnly = true)
    public ArtistSongDetailsDto getArtistSongDetails(Long releaseId, User currentUser) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        if (release.getArtist() == null || !release.getArtist().getId().equals(currentUser.getId())) {
            throw new AppException("Access denied: Only the owning artist may view this page", HttpStatus.FORBIDDEN);
        }

        long totalPlays = musicPlayRepository.countByRelease(release);
        long uniqueListeners = musicPlayRepository.countDistinctUsersByRelease(release);

        List<Object[]> userPlayCounts = musicPlayRepository.countPlaysPerUserByRelease(release);
        long replays = 0;
        for (Object[] row : userPlayCounts) {
            long count = ((Number) row[1]).longValue();
            if (count > 1) {
                replays += (count - 1);
            }
        }

        // Activity 7 Days
        LocalDate today = LocalDate.now();
        List<ArtistSongDetailsDto.ListeningActivityPointDto> activity7Days = new ArrayList<>();
        DateTimeFormatter dtfDay = DateTimeFormatter.ofPattern("EEE");
        DateTimeFormatter dtfFull = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        List<MusicPlay> playsPast7Days = musicPlayRepository.findByReleaseAndPlayedAtAfter(
                release, today.minusDays(6).atStartOfDay()
        );

        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long dayCount = playsPast7Days.stream()
                    .filter(p -> p.getPlayedAt() != null && p.getPlayedAt().toLocalDate().isEqual(day))
                    .count();
            activity7Days.add(ArtistSongDetailsDto.ListeningActivityPointDto.builder()
                    .date(day.format(dtfFull))
                    .label(day.format(dtfDay))
                    .count(dayCount)
                    .build());
        }

        // Activity 30 Days
        List<ArtistSongDetailsDto.ListeningActivityPointDto> activity30Days = new ArrayList<>();
        DateTimeFormatter dtfShort = DateTimeFormatter.ofPattern("M/d");
        List<MusicPlay> playsPast30Days = musicPlayRepository.findByReleaseAndPlayedAtAfter(
                release, today.minusDays(29).atStartOfDay()
        );

        for (int i = 29; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            long dayCount = playsPast30Days.stream()
                    .filter(p -> p.getPlayedAt() != null && p.getPlayedAt().toLocalDate().isEqual(day))
                    .count();
            activity30Days.add(ArtistSongDetailsDto.ListeningActivityPointDto.builder()
                    .date(day.format(dtfFull))
                    .label(day.format(dtfShort))
                    .count(dayCount)
                    .build());
        }

        // Activity All Time (Grouped by month or days)
        List<ArtistSongDetailsDto.ListeningActivityPointDto> activityAllTime = new ArrayList<>();
        List<MusicPlay> allPlays = musicPlayRepository.findByReleaseOrderByPlayedAtAsc(release);
        if (!allPlays.isEmpty()) {
            DateTimeFormatter dtfMonth = DateTimeFormatter.ofPattern("MMM yy");
            Map<String, Long> monthCounts = new LinkedHashMap<>();
            for (MusicPlay p : allPlays) {
                if (p.getPlayedAt() != null) {
                    String monthKey = p.getPlayedAt().format(dtfMonth);
                    monthCounts.put(monthKey, monthCounts.getOrDefault(monthKey, 0L) + 1);
                }
            }
            monthCounts.forEach((label, count) -> {
                activityAllTime.add(ArtistSongDetailsDto.ListeningActivityPointDto.builder()
                        .date(label)
                        .label(label)
                        .count(count)
                        .build());
            });
        }

        // Access & Purchases
        long accessGranted = accessRepository.countByReleaseAndStatus(release, MusicAccessStatus.ACTIVE);
        List<MusicAccess> activeAccesses = accessRepository.findByReleaseAndStatusOrderByGrantedAtDesc(release, MusicAccessStatus.ACTIVE);
        long purchases = activeAccesses.stream()
                .filter(a -> a.getTransaction() != null && !a.isGift())
                .count();

        boolean isPaid = release.getAlbumPrice() != null && release.getAlbumPrice().compareTo(BigDecimal.ZERO) > 0;

        int trackCount = (release.getTracks() != null) ? release.getTracks().size() : 0;
        int totalDurationSeconds = 0;
        if (release.getTracks() != null) {
            for (MusicTrack t : release.getTracks()) {
                if (t.getDurationSeconds() != null) {
                    totalDurationSeconds += t.getDurationSeconds();
                }
            }
        }
        if (totalDurationSeconds == 0) {
            totalDurationSeconds = 180 * Math.max(1, trackCount);
        }

        int mins = totalDurationSeconds / 60;
        int secs = totalDurationSeconds % 60;
        String formattedDuration = String.format("%d:%02d", mins, secs);

        String releaseDateFormatted = "";
        if (release.getReleaseDate() != null) {
            releaseDateFormatted = release.getReleaseDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
        } else if (release.getCreatedAt() != null) {
            releaseDateFormatted = release.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM dd, yyyy"));
        }

        String artistName = release.getArtist().getFullName() != null && !release.getArtist().getFullName().trim().isEmpty()
                ? release.getArtist().getFullName()
                : release.getArtist().getUsername();

        return ArtistSongDetailsDto.builder()
                .release(release)
                .id(release.getId())
                .title(release.getTitle())
                .artistName(artistName)
                .artistUsername(release.getArtist().getUsername())
                .coverArtUrl(release.getCoverArtUrl())
                .status(release.getStatus() != null ? release.getStatus() : "PUBLISHED")
                .durationSeconds(totalDurationSeconds)
                .formattedDuration(formattedDuration)
                .releaseDate(releaseDateFormatted)
                .totalPlays(totalPlays)
                .uniqueListeners(uniqueListeners)
                .replays(replays)
                .activity7Days(activity7Days)
                .activity30Days(activity30Days)
                .activityAllTime(activityAllTime)
                .isPaidRelease(isPaid)
                .accessGranted(accessGranted)
                .purchases(purchases)
                .genre(release.getGenre() != null ? release.getGenre() : "Afrobeats")
                .subgenre(release.getSubgenre())
                .releaseType(release.getReleaseType() != null ? release.getReleaseType().name() : "SINGLE")
                .price(release.getAlbumPrice() != null ? release.getAlbumPrice() : BigDecimal.ZERO)
                .description(release.getDescription())
                .trackCount(trackCount)
                .releaseStory(release.getReleaseStory())
                .tracks(release.getTracks() == null ? java.util.Collections.emptyList() :
                        release.getTracks().stream()
                                .sorted(java.util.Comparator.comparingInt(t -> t.getTrackNumber() != null ? t.getTrackNumber() : 0))
                                .map(t -> ArtistSongDetailsDto.TrackStoryDto.builder()
                                        .id(t.getId())
                                        .trackNumber(t.getTrackNumber())
                                        .title(t.getTitle())
                                        .trackStory(t.getTrackStory())
                                        .producer(t.getProducer())
                                        .songwriter(t.getSongwriter())
                                        .featuredArtists(t.getFeaturedArtists())
                                        .isExplicit(t.isExplicit())
                                        .isBonusTrack(t.isBonusTrack())
                                        .durationSeconds(t.getDurationSeconds())
                                        .build())
                                .collect(Collectors.toList()))
                .build();
    }

    @Transactional
    public MusicRelease archiveRelease(Long releaseId, User currentUser) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        if (release.getArtist() == null || !release.getArtist().getId().equals(currentUser.getId())) {
            throw new AppException("Access denied: Only the owning artist may archive this release", HttpStatus.FORBIDDEN);
        }
        if (!MusicRelease.STATUS_PUBLISHED.equalsIgnoreCase(release.getStatus())) {
            throw new AppException("Only published releases can be archived", HttpStatus.CONFLICT);
        }

        release.setStatus(MusicRelease.STATUS_ARCHIVED);
        return releaseRepository.save(release);
    }

    @Transactional
    public MusicRelease unarchiveRelease(Long releaseId, User currentUser) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));

        if (release.getArtist() == null || !release.getArtist().getId().equals(currentUser.getId())) {
            throw new AppException("Access denied: Only the owning artist may modify this release", HttpStatus.FORBIDDEN);
        }
        if (!MusicRelease.STATUS_ARCHIVED.equalsIgnoreCase(release.getStatus())) {
            throw new AppException("Only archived releases can be unarchived", HttpStatus.CONFLICT);
        }

        // Going back on sale requires a prior administrator approval; anything else returns to
        // draft so it has to pass review first.
        release.setStatus(release.getApprovedAt() != null ? MusicRelease.STATUS_PUBLISHED : MusicRelease.STATUS_DRAFT);
        return releaseRepository.save(release);
    }
}

