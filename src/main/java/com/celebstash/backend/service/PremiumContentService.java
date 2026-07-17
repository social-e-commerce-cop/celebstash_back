package com.celebstash.backend.service;

import com.celebstash.backend.dto.premium.PremiumContentRequest;
import com.celebstash.backend.dto.premium.PremiumContentResponse;
import com.celebstash.backend.dto.premium.StreamUrlResponse;
import com.celebstash.backend.dto.premium.PlaySyncRequest;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.*;
import com.celebstash.backend.repository.*;
import com.celebstash.backend.util.UrlSigner;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PremiumContentService {

    private final PremiumContentRepository premiumContentRepository;
    private final UnlockedContentRepository unlockedContentRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final UrlSigner urlSigner;

    @Transactional
    public PremiumContentResponse uploadContent(PremiumContentRequest request, String fileName) {
        User creator = userService.getCurrentUser();

        if (creator.getRole() != Role.ARTIST || !creator.isAccountVerified()) {
            throw new AppException("Only verified creators can upload premium content", HttpStatus.FORBIDDEN);
        }

        PremiumContent content = PremiumContent.builder()
                .creator(creator)
                .title(request.getTitle())
                .description(request.getDescription())
                .fileName(fileName)
                .coverImageUrl(request.getCoverImageUrl())
                .accessModel(request.getAccessModel())
                .price(request.getPrice())
                .earlyAccessUntil(request.getEarlyAccessUntil())
                .build();

        PremiumContent saved = premiumContentRepository.save(content);
        return mapToResponse(saved, creator);
    }

    @Transactional
    public PremiumContentResponse unlockContent(Long contentId, String pin) {
        User currentUser = userService.getCurrentUser();
        PremiumContent content = premiumContentRepository.findById(contentId)
                .orElseThrow(() -> new AppException("Premium content not found", HttpStatus.NOT_FOUND));

        // Check if already unlocked
        if (hasAccess(currentUser, content)) {
            throw new AppException("Content already unlocked", HttpStatus.BAD_REQUEST);
        }

        Wallet userWallet = walletRepository.findByUser(currentUser)
                .orElseThrow(() -> new AppException("Wallet not found", HttpStatus.NOT_FOUND));

        if (userWallet.getPin() == null || !userWallet.getPin().equals(pin)) {
            throw new AppException("Invalid PIN", HttpStatus.BAD_REQUEST);
        }

        if (userWallet.getBalance().compareTo(content.getPrice()) < 0) {
            throw new AppException("Insufficient balance", HttpStatus.BAD_REQUEST);
        }

        // Deduct from buyer
        userWallet.setBalance(userWallet.getBalance().subtract(content.getPrice()));
        walletRepository.save(userWallet);

        // Credit to creator
        User creator = content.getCreator();
        Wallet creatorWallet = walletRepository.findByUser(creator)
                .orElseGet(() -> {
                    Wallet newWallet = Wallet.builder()
                            .user(creator)
                            .balance(BigDecimal.ZERO)
                            .createdAt(LocalDateTime.now())
                            .build();
                    return walletRepository.save(newWallet);
                });
        creatorWallet.setBalance(creatorWallet.getBalance().add(content.getPrice()));
        walletRepository.save(creatorWallet);

        // Save unlock record
        UnlockedContent unlock = UnlockedContent.builder()
                .user(currentUser)
                .content(content)
                .unlockedAt(LocalDateTime.now())
                .build();
        
        // Handle pay-per-listen timeboxed models (e.g. valid for 48 hours)
        if (content.getAccessModel() == PremiumAccessModel.PAY_PER_LISTEN) {
            unlock.setExpiresAt(LocalDateTime.now().plusHours(48));
        }
        unlockedContentRepository.save(unlock);

        // Record transactions
        Transaction buyerTx = Transaction.builder()
                .wallet(userWallet)
                .amount(content.getPrice())
                .type(TransactionType.PAYMENT)
                .status(TransactionStatus.COMPLETED)
                .description("Unlock premium content: " + content.getTitle())
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(buyerTx);

        Transaction creatorTx = Transaction.builder()
                .wallet(creatorWallet)
                .amount(content.getPrice())
                .type(TransactionType.DEPOSIT)
                .status(TransactionStatus.COMPLETED)
                .description("Premium content unlock payout: " + content.getTitle())
                .createdAt(LocalDateTime.now())
                .completedAt(LocalDateTime.now())
                .build();
        transactionRepository.save(creatorTx);

        return mapToResponse(content, currentUser);
    }

    @Transactional
    public StreamUrlResponse getStreamUrl(Long contentId) {
        User currentUser = userService.getCurrentUser();
        PremiumContent content = premiumContentRepository.findById(contentId)
                .orElseThrow(() -> new AppException("Premium content not found", HttpStatus.NOT_FOUND));

        if (!hasAccess(currentUser, content)) {
            throw new AppException("Access denied: You must unlock this content first", HttpStatus.FORBIDDEN);
        }

        // Decrement remaining plays if user is not the creator or admin
        if (!content.getCreator().getId().equals(currentUser.getId()) && currentUser.getRole() != Role.ADMIN) {
            UnlockedContent unlock = unlockedContentRepository.findByUserAndContent(currentUser, content)
                    .orElseThrow(() -> new AppException("Access denied", HttpStatus.FORBIDDEN));
            if (unlock.getRemainingPlays() != null) {
                if (unlock.getRemainingPlays() <= 0) {
                    throw new AppException("You have run out of plays. Please purchase/unlock again.", HttpStatus.FORBIDDEN);
                }
                unlock.setRemainingPlays(unlock.getRemainingPlays() - 1);
                unlockedContentRepository.save(unlock);
            }
        }

        // Generate signed URL valid for 1 hour
        long expiresAt = System.currentTimeMillis() + 3600000;
        String path = "/api/files/stream/" + content.getFileName();
        String token = urlSigner.generateSignature(path, expiresAt);

        String signedStreamUri = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path(path)
                .queryParam("token", token)
                .queryParam("expires", expiresAt)
                .toUriString();

        return new StreamUrlResponse(signedStreamUri);
    }

    @Transactional
    public void syncPlays(List<PlaySyncRequest> syncRequests) {
        User currentUser = userService.getCurrentUser();
        for (PlaySyncRequest req : syncRequests) {
            PremiumContent content = premiumContentRepository.findById(req.getContentId()).orElse(null);
            if (content != null) {
                unlockedContentRepository.findByUserAndContent(currentUser, content).ifPresent(unlock -> {
                    if (unlock.getRemainingPlays() != null) {
                        int remaining = unlock.getRemainingPlays() - req.getPlaysCount();
                        unlock.setRemainingPlays(Math.max(0, remaining));
                        unlockedContentRepository.save(unlock);
                    }
                });
            }
        }
    }

    @Transactional(readOnly = true)
    public List<PremiumContentResponse> getCreatorPremiumContent(Long creatorId) {
        User currentUser = userService.getCurrentUser();
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new AppException("Creator not found", HttpStatus.NOT_FOUND));

        return premiumContentRepository.findByCreator(creator).stream()
                .map(content -> mapToResponse(content, currentUser))
                .collect(Collectors.toList());
    }

    private boolean hasAccess(User user, PremiumContent content) {
        // Creator always has access
        if (content.getCreator().getId().equals(user.getId())) {
            return true;
        }
        
        // Admin always has access
        if (user.getRole() == Role.ADMIN) {
            return true;
        }

        return unlockedContentRepository.findByUserAndContent(user, content)
                .map(unlock -> (unlock.getExpiresAt() == null || unlock.getExpiresAt().isAfter(LocalDateTime.now()))
                        && (unlock.getRemainingPlays() == null || unlock.getRemainingPlays() > 0))
                .orElse(false);
    }

    private PremiumContentResponse mapToResponse(PremiumContent content, User user) {
        PremiumContentResponse.PremiumContentResponseBuilder builder = PremiumContentResponse.builder()
                .id(content.getId())
                .creatorId(content.getCreator().getId())
                .creatorName(content.getCreator().getFullName())
                .title(content.getTitle())
                .description(content.getDescription())
                .coverImageUrl(content.getCoverImageUrl())
                .accessModel(content.getAccessModel())
                .price(content.getPrice())
                .earlyAccessUntil(content.getEarlyAccessUntil())
                .createdAt(content.getCreatedAt())
                .isUnlocked(hasAccess(user, content));

        unlockedContentRepository.findByUserAndContent(user, content).ifPresent(unlock -> {
            builder.remainingPlays(unlock.getRemainingPlays());
            builder.playCap(unlock.getPlayCap());
        });

        return builder.build();
    }
}
