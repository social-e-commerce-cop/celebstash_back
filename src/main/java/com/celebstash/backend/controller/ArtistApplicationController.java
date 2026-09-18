package com.celebstash.backend.controller;

import com.celebstash.backend.dto.artist.ArtistApplicationRequest;
import com.celebstash.backend.dto.artist.ArtistApplicationResponse;
import com.celebstash.backend.dto.artist.ReviewApplicationRequest;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ApplicationStatus;
import com.celebstash.backend.service.ArtistApplicationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ArtistApplicationController {

    private final ArtistApplicationService applicationService;

    // Submit an application to become an artist
    @PostMapping("/artist-applications")
    public ResponseEntity<ArtistApplicationResponse> submitApplication(
            @AuthenticationPrincipal User user,
            @Valid @RequestBody ArtistApplicationRequest request
    ) {
        ArtistApplicationResponse response = applicationService.submitApplication(user, request);
        return ResponseEntity.ok(response);
    }

    // Get current user's artist application status
    @GetMapping("/artist-applications/my-status")
    public ResponseEntity<ArtistApplicationResponse> getMyApplicationStatus(
            @AuthenticationPrincipal User user
    ) {
        return applicationService.getUserApplicationStatus(user)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    // ADMIN: List all applications (optional status filter)
    @GetMapping("/admin/artist-applications")
    public ResponseEntity<List<ArtistApplicationResponse>> getAllApplications(
            @RequestParam(required = false) ApplicationStatus status
    ) {
        List<ArtistApplicationResponse> applications = applicationService.getAllApplications(status);
        return ResponseEntity.ok(applications);
    }

    // ADMIN: Review (Approve / Reject) an application
    @PostMapping("/admin/artist-applications/{id}/review")
    public ResponseEntity<ArtistApplicationResponse> reviewApplication(
            @PathVariable Long id,
            @RequestBody ReviewApplicationRequest reviewRequest
    ) {
        ArtistApplicationResponse response = applicationService.reviewApplication(id, reviewRequest);
        return ResponseEntity.ok(response);
    }
}
