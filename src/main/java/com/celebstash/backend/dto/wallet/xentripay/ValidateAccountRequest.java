package com.celebstash.backend.dto.wallet.xentripay;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateAccountRequest {
    @NotBlank(message = "Account or phone number is required")
    private String accountNumber;

    @NotBlank(message = "Provider (bank or mobile money code) is required")
    private String provider;
}
