package com.celebstash.backend.controller;

import com.celebstash.backend.dto.kyc.KycResponse;
import com.celebstash.backend.dto.kyc.KycReviewRequest;
import com.celebstash.backend.dto.kyc.KycSubmitRequest;
import com.celebstash.backend.service.KycService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kyc")
@RequiredArgsConstructor
@Tag(name = "KYC Onboarding", description = "Creator onboarding and identity verification APIs")
public class KycController {

    private final KycService kycService;

    @PostMapping("/submit")
    @Operation(summary = "Submit KYC request", description = "Allows a normal user to submit details to apply to be a creator")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<KycResponse> submitKyc(@Valid @RequestBody KycSubmitRequest request) {
        return new ResponseEntity<>(kycService.submitKyc(request), HttpStatus.CREATED);
    }

    @GetMapping("/my-status")
    @Operation(summary = "Get my KYC status", description = "Returns the verification details of the logged-in user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<KycResponse> getMyKycStatus() {
        return ResponseEntity.ok(kycService.getMyKycStatus());
    }

    @GetMapping("/pending")
    @Operation(summary = "List pending KYC requests", description = "Allows admins to view all pending onboarding requests")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<KycResponse>> getPendingKycRequests() {
        return ResponseEntity.ok(kycService.getPendingKycRequests());
    }

    @PostMapping("/{id}/review")
    @Operation(summary = "Review KYC request", description = "Allows admins to approve or reject a KYC onboarding request")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<KycResponse> reviewKycRequest(
            @PathVariable Long id,
            @Valid @RequestBody KycReviewRequest request) {
        return ResponseEntity.ok(kycService.reviewKycRequest(id, request));
    }
}
