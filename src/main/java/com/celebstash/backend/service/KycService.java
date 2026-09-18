package com.celebstash.backend.service;

import com.celebstash.backend.dto.kyc.KycResponse;
import com.celebstash.backend.dto.kyc.KycReviewRequest;
import com.celebstash.backend.dto.kyc.KycSubmitRequest;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.KycRequest;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.KycStatus;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.KycRepository;
import com.celebstash.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class KycService {

    private final KycRepository kycRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationService notificationService;
    private final JavaMailSender emailSender;
    
    private final String ADMIN_EMAIL = "karabogretta@gmail.com";

    @Transactional
    public KycResponse submitKyc(KycSubmitRequest request) {
        User currentUser = userService.getCurrentUser();

        // Check if user already has an active or pending request
        kycRepository.findByUser(currentUser).ifPresent(existing -> {
            if (existing.getStatus() == KycStatus.PENDING) {
                throw new AppException("You already have a pending verification request", HttpStatus.BAD_REQUEST);
            }
            if (existing.getStatus() == KycStatus.APPROVED) {
                throw new AppException("Your account is already verified as a creator", HttpStatus.BAD_REQUEST);
            }
        });

        // Delete previous rejected requests to allow re-submission
        kycRepository.findByUser(currentUser).ifPresent(existing -> {
            if (existing.getStatus() == KycStatus.REJECTED) {
                kycRepository.delete(existing);
            }
        });

        KycRequest kycRequest = KycRequest.builder()
                .user(currentUser)
                .idDocumentUrl(request.getIdDocumentUrl())
                .socialMediaLink(request.getSocialMediaLink())
                .status(KycStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .build();

        KycRequest saved = kycRepository.save(kycRequest);

        // Notify user that request is submitted
        notificationService.createNotification(
                currentUser,
                "Verification Submitted",
                "Your creator verification request has been submitted and is currently pending review by an admin.",
                NotificationType.KYC_STATUS
        );
        
        // Notify admin via email
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(ADMIN_EMAIL);
            message.setSubject("New Creator Verification Request: " + currentUser.getFullName());
            message.setText("A new creator verification (KYC) request has been submitted by " + currentUser.getFullName() + 
                    " (" + currentUser.getEmail() + ").\n\nSocial Media Link: " + request.getSocialMediaLink() + 
                    "\n\nPlease review the documents in the admin dashboard.");
            emailSender.send(message);
        } catch (Exception e) {
            System.err.println("Failed to send admin notification email: " + e.getMessage());
        }

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public KycResponse getMyKycStatus() {
        User currentUser = userService.getCurrentUser();
        KycRequest request = kycRepository.findByUser(currentUser)
                .orElseThrow(() -> new AppException("No verification request found for this user", HttpStatus.NOT_FOUND));
        return mapToResponse(request);
    }

    @Transactional(readOnly = true)
    public List<KycResponse> getPendingKycRequests() {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getRole() != Role.ADMIN) {
            throw new AppException("Access denied: Admin only", HttpStatus.FORBIDDEN);
        }

        return kycRepository.findByStatus(KycStatus.PENDING).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public KycResponse reviewKycRequest(Long requestId, KycReviewRequest request) {
        User admin = userService.getCurrentUser();
        if (admin.getRole() != Role.ADMIN) {
            throw new AppException("Access denied: Admin only", HttpStatus.FORBIDDEN);
        }

        KycRequest kycRequest = kycRepository.findById(requestId)
                .orElseThrow(() -> new AppException("KYC request not found", HttpStatus.NOT_FOUND));

        if (kycRequest.getStatus() != KycStatus.PENDING) {
            throw new AppException("This request has already been reviewed", HttpStatus.BAD_REQUEST);
        }

        kycRequest.setStatus(request.getStatus());
        kycRequest.setAdminNotes(request.getAdminNotes());
        kycRequest.setReviewedAt(LocalDateTime.now());
        kycRequest.setReviewedBy(admin);

        User applicant = kycRequest.getUser();
        if (request.getStatus() == KycStatus.APPROVED) {
            applicant.setRole(Role.ARTIST);
            applicant.setAccountVerified(true);
            applicant.setAccountVerifiedAt(LocalDateTime.now());
            
            notificationService.createNotification(
                    applicant,
                    "Creator Verification Approved",
                    "Congratulations! Your account has been verified as a creator. You can now post stories, feed content, list products, and upload premium media.",
                    NotificationType.KYC_STATUS
            );
        } else {
            applicant.setRole(Role.USER);
            applicant.setAccountVerified(false);

            notificationService.createNotification(
                    applicant,
                    "Creator Verification Rejected",
                    "Your creator verification request was rejected. Reason: " + request.getAdminNotes(),
                    NotificationType.KYC_STATUS
            );
        }
        userRepository.save(applicant);
        KycRequest updated = kycRepository.save(kycRequest);

        return mapToResponse(updated);
    }

    private KycResponse mapToResponse(KycRequest request) {
        return KycResponse.builder()
                .id(request.getId())
                .userId(request.getUser().getId())
                .userName(request.getUser().getFullName())
                .userEmail(request.getUser().getEmail())
                .idDocumentUrl(request.getIdDocumentUrl())
                .socialMediaLink(request.getSocialMediaLink())
                .status(request.getStatus())
                .adminNotes(request.getAdminNotes())
                .createdAt(request.getCreatedAt())
                .reviewedAt(request.getReviewedAt())
                .reviewedByName(request.getReviewedBy() != null ? request.getReviewedBy().getFullName() : null)
                .build();
    }
}
