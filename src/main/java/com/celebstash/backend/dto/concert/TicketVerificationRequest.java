package com.celebstash.backend.dto.concert;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketVerificationRequest {

    @NotBlank(message = "Verification code is required")
    private String verificationCode;
}
