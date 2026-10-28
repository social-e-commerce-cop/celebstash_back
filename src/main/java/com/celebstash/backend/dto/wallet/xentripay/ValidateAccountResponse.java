package com.celebstash.backend.dto.wallet.xentripay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateAccountResponse {
    private String accountNumber;
    private String providerId;
    private String providerName;
    private String registeredName;
    private boolean valid;
}
