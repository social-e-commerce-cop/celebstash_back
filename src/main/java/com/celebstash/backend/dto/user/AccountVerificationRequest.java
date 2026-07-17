package com.celebstash.backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountVerificationRequest {

    @NotBlank(message = "Verification reason is required")
    private String verificationReason;

    // Additional fields that might be required for verification
    private String identificationDocument;
    
    private String contactInformation;
}