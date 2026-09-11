package com.celebstash.backend.service;

import com.celebstash.backend.dto.artist.ArtistApplicationRequest;
import com.celebstash.backend.dto.artist.ArtistApplicationResponse;
import com.celebstash.backend.dto.artist.ReviewApplicationRequest;
import com.celebstash.backend.model.ArtistApplication;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ApplicationStatus;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.ArtistApplicationRepository;
import com.celebstash.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArtistApplicationService {

    private final ArtistApplicationRepository applicationRepository;
    private final UserRepository userRepository;
    private final JavaMailSender emailSender;
    private final NotificationService notificationService;
    
    @Value("${spring.mail.username:karabogretta@gmail.com}")
    private String mailFrom;

    private final String ADMIN_EMAIL = "karabogretta@gmail.com";

    @Transactional
    public ArtistApplicationResponse submitApplication(User user, ArtistApplicationRequest request) {
        // Check if user already has an active pending or approved application
        Optional<ArtistApplication> existingOpt = applicationRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (existingOpt.isPresent()) {
            ApplicationStatus status = existingOpt.get().getStatus();
            if (status == ApplicationStatus.PENDING) {
                throw new IllegalArgumentException("You already have an artist application pending review.");
            }
            if (status == ApplicationStatus.APPROVED) {
                throw new IllegalArgumentException("Your account is already a verified Artist.");
            }
        }

        ArtistApplication application = ArtistApplication.builder()
                .user(user)
                .stageName(request.getStageName())
                .category(request.getCategory())
                .bio(request.getBio())
                .socialProofLink(request.getSocialProofLink())
                .status(ApplicationStatus.PENDING)
                .build();

        ArtistApplication saved = applicationRepository.save(application);
        log.info("Artist application submitted by user: {} ({})", user.getEmail(), saved.getStageName());
        
        // 1. Create in-app Notification for applicant
        try {
            notificationService.createNotification(
                    user,
                    "Artist Application Submitted",
                    "Your application for " + saved.getStageName() + " has been submitted successfully and is currently under review by our team.",
                    NotificationType.APPLICATION_SUBMITTED,
                    saved.getId()
            );
        } catch (Exception e) {
            log.error("Failed to create in-app notification for application submission: {}", e.getMessage());
        }

        // 2. Send confirmation email to applicant
        // 2. Send confirmation email to applicant asynchronously
        if (user != null && user.getEmail() != null && !user.getEmail().isBlank()) {
            final String recipient = user.getEmail();
            final String fullName = user.getFullName();
            final String stage = saved.getStageName();
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    SimpleMailMessage userEmail = new SimpleMailMessage();
                    userEmail.setFrom(mailFrom);
                    userEmail.setTo(recipient);
                    userEmail.setSubject("Zikii Artist Application Received");
                    userEmail.setText("Hello " + fullName + ",\n\n" +
                            "We have received your artist application for " + stage + ".\n\n" +
                            "Our team is currently reviewing your application details and social proof. You will receive a notification as soon as a decision is made.\n\n" +
                            "Best regards,\n" +
                            "Zikii Team");
                    emailSender.send(userEmail);
                    log.info("Confirmation email sent to {}", recipient);
                } catch (Exception e) {
                    log.error("Failed to send applicant confirmation email to {}: {}", recipient, e.getMessage());
                }
            });
        }

        // 3. Notify admin via email asynchronously
        final String adminStage = saved.getStageName();
        final String adminCategory = saved.getCategory();
        final String adminBio = saved.getBio();
        final String adminSocial = saved.getSocialProofLink();
        final String userFull = user != null ? user.getFullName() : "N/A";
        final String userMail = user != null ? user.getEmail() : "N/A";
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(mailFrom);
                message.setTo(ADMIN_EMAIL);
                message.setSubject("New Artist Application: " + adminStage);
                message.setText("A new artist application has been submitted by " + userFull + 
                        " (" + userMail + ") for the stage name: " + adminStage + 
                        ".\n\nCategory: " + adminCategory + 
                        "\nBio: " + adminBio + 
                        "\nSocial Proof: " + adminSocial + 
                        "\n\nPlease review it in the admin dashboard.");
                emailSender.send(message);
                log.info("Admin notification email sent to {}", ADMIN_EMAIL);
            } catch (Exception e) {
                log.error("Failed to send admin notification email: {}", e.getMessage());
            }
        });
        
        return mapToResponse(saved);
    }

    public Optional<ArtistApplicationResponse> getUserApplicationStatus(User user) {
        return applicationRepository.findTopByUserOrderByCreatedAtDesc(user)
                .map(this::mapToResponse);
    }

    public ArtistApplicationResponse getApplicationById(Long id) {
        ArtistApplication application = applicationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Artist application not found with id: " + id));
        return mapToResponse(application);
    }

    public List<ArtistApplicationResponse> getAllApplications(ApplicationStatus status) {
        List<ArtistApplication> list = status != null
                ? applicationRepository.findByStatusOrderByCreatedAtDesc(status)
                : applicationRepository.findAllByOrderByCreatedAtDesc();

        return list.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional
    public ArtistApplicationResponse reviewApplication(Long applicationId, ReviewApplicationRequest reviewRequest) {
        ArtistApplication application = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new IllegalArgumentException("Artist application not found with id: " + applicationId));

        User user = application.getUser();

        if (reviewRequest.isApproved()) {
            application.setStatus(ApplicationStatus.APPROVED);
            application.setRejectionReason(null);

            // Re-tag user role as ARTIST
            user.setRole(Role.ARTIST);
            userRepository.save(user);

            log.info("Artist application APPROVED for user {} ({})", user.getEmail(), application.getStageName());

            // Send in-app notification
            try {
                notificationService.createNotification(
                        user,
                        "Artist Application Approved! 🎉",
                        "Congratulations! Your application for " + application.getStageName() + " has been approved. Your verified Artist features are now active.",
                        NotificationType.APPLICATION_APPROVED,
                        application.getId()
                );
            } catch (Exception e) {
                log.error("Failed to create approval notification: {}", e.getMessage());
            }

            // Send approval email asynchronously
            String approvalRecipient = (user != null && user.getEmail() != null && !user.getEmail().isBlank()) ? user.getEmail() : ADMIN_EMAIL;
            if (approvalRecipient.endsWith("@zikiii.com") || approvalRecipient.endsWith("@example.com")) {
                approvalRecipient = ADMIN_EMAIL;
            }

            final String appRecipient = approvalRecipient;
            final String appStage = application.getStageName();
            final String appName = (user != null ? user.getFullName() : application.getStageName());
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    SimpleMailMessage email = new SimpleMailMessage();
                    email.setFrom(mailFrom);
                    email.setTo(appRecipient);
                    email.setSubject("Welcome to Zikii Verified Artists! 🎉");
                    email.setText("Hello " + appName + ",\n\n" +
                            "Congratulations! Your application for artist status (" + appStage + ") has been approved by our admin team.\n\n" +
                            "You now have verified artist status with access to upload music, sell products, and host concerts on Zikii.\n\n" +
                            "Best regards,\n" +
                            "Zikii Team");
                    emailSender.send(email);
                    log.info("Approval email successfully sent to {}", appRecipient);
                } catch (Exception e) {
                    log.error("Failed to send approval email to {}: {}", appRecipient, e.getMessage());
                }
            });
        } else {
            // Mandatory rejection reason validation
            String reason = reviewRequest.getRejectionReason();
            if (reason == null || reason.trim().isEmpty()) {
                reason = reviewRequest.getReviewNotes();
            }
            if (reason == null || reason.trim().isEmpty()) {
                throw new IllegalArgumentException("Rejection reason is mandatory when declining an artist application.");
            }

            String finalReason = reason.trim();
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason(finalReason);

            log.info("Artist application REJECTED for user {} with reason: {}", user != null ? user.getEmail() : "N/A", finalReason);

            // Send in-app notification
            if (user != null) {
                try {
                    notificationService.createNotification(
                            user,
                            "Artist Application Declined",
                            "Your application for " + application.getStageName() + " was declined. Reason: " + finalReason,
                            NotificationType.APPLICATION_REJECTED,
                            application.getId()
                    );
                } catch (Exception e) {
                    log.error("Failed to create rejection notification: {}", e.getMessage(), e);
                }
            }

            // Send rejection email asynchronously
            String rejectionRecipient = (user != null && user.getEmail() != null && !user.getEmail().isBlank()) ? user.getEmail() : ADMIN_EMAIL;
            if (rejectionRecipient.endsWith("@zikiii.com") || rejectionRecipient.endsWith("@example.com")) {
                rejectionRecipient = ADMIN_EMAIL;
            }

            final String rejRecipient = rejectionRecipient;
            final String rejStage = application.getStageName();
            final String rejName = (user != null ? user.getFullName() : application.getStageName());
            final String rejReason = finalReason;
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    SimpleMailMessage email = new SimpleMailMessage();
                    email.setFrom(mailFrom);
                    email.setTo(rejRecipient);
                    email.setSubject("Zikii Artist Application Status Update");
                    email.setText("Hello " + rejName + ",\n\n" +
                            "Your application for artist status (" + rejStage + ") was reviewed by our admin team and was declined.\n\n" +
                            "Reason for Rejection:\n" + rejReason + "\n\n" +
                            "You may update your details and submit a new application when ready.\n\n" +
                            "Best regards,\n" +
                            "Zikii Admin Team");
                    emailSender.send(email);
                    log.info("Rejection email successfully sent to {}", rejRecipient);
                } catch (Exception e) {
                    log.error("Failed to send rejection email to {}: {}", rejRecipient, e.getMessage());
                }
            });
        }

        ArtistApplication updated = applicationRepository.save(application);
        return mapToResponse(updated);
    }

    private ArtistApplicationResponse mapToResponse(ArtistApplication app) {
        User u = app.getUser();
        return ArtistApplicationResponse.builder()
                .id(app.getId())
                .userId(u != null ? u.getId() : null)
                .userFullName(u != null ? u.getFullName() : app.getStageName())
                .userEmail(u != null ? u.getEmail() : null)
                .username(u != null ? u.getUsername() : null)
                .userProfilePicture(u != null ? u.getProfilePicture() : null)
                .stageName(app.getStageName())
                .category(app.getCategory())
                .bio(app.getBio())
                .socialProofLink(app.getSocialProofLink())
                .status(app.getStatus())
                .rejectionReason(app.getRejectionReason())
                .createdAt(app.getCreatedAt())
                .updatedAt(app.getUpdatedAt())
                .build();
    }
}
