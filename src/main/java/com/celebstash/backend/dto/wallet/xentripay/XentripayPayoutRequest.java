package com.celebstash.backend.dto.wallet.xentripay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XentripayPayoutRequest {
    private String customerReference;
    private String telecomProviderId;
    private String msisdn;
    private String name;
    private String transactionType; // "PAYOUT"
    private String currency; // "RWF"
    private Integer amount;
}
