package com.celebstash.backend.service;

import com.celebstash.backend.dto.artist.ArtistApplicationRequest;
import com.celebstash.backend.dto.artist.ArtistApplicationResponse;
import com.celebstash.backend.dto.artist.ReviewApplicationRequest;
import com.celebstash.backend.model.ArtistApplication;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ApplicationStatus;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.ArtistApplicationRepository;
import com.celebstash.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Transactional
    public ArtistApplicationResponse submitApplication(User user, ArtistApplicationRequest request) {
        // Check if user already has an active pending application
        Optional<ArtistApplication> existingOpt = applicationRepository.findTopByUserOrderByCreatedAtDesc(user);
        if (existingOpt.isPresent() && existingOpt.get().getStatus() == ApplicationStatus.PENDING) {
            throw new IllegalArgumentException("You already have an artist application pending review.");
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
        return mapToResponse(saved);
    }

    public Optional<ArtistApplicationResponse> getUserApplicationStatus(User user) {
        return applicationRepository.findTopByUserOrderByCreatedAtDesc(user)
                .map(this::mapToResponse);
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
                .orElseThrow(() -> new IllegalArgumentException("Artist application not found with ID: " + applicationId));

        User user = application.getUser();

        if (reviewRequest.isApprove()) {
            application.setStatus(ApplicationStatus.APPROVED);
            application.setRejectionReason(null);

            // Re-tag user role as ARTIST
            user.setRole(Role.ARTIST);
            userRepository.save(user);

            log.info("Artist application APPROVED for user {} ({})", user.getEmail(), application.getStageName());
        } else {
            application.setStatus(ApplicationStatus.REJECTED);
            application.setRejectionReason(reviewRequest.getRejectionReason() != null ? reviewRequest.getRejectionReason() : "Application criteria not met.");

            log.info("Artist application REJECTED for user {}", user.getEmail());
        }

        ArtistApplication updated = applicationRepository.save(application);
        return mapToResponse(updated);
    }

    private ArtistApplicationResponse mapToResponse(ArtistApplication app) {
        return ArtistApplicationResponse.builder()
                .id(app.getId())
                .userId(app.getUser().getId())
                .userFullName(app.getUser().getFullName())
                .userEmail(app.getUser().getEmail())
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
