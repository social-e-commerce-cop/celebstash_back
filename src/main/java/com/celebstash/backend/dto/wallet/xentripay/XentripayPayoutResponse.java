package com.celebstash.backend.dto.wallet.xentripay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XentripayPayoutResponse {
    private Long id;
    private String businessName;
    private String customerReference;
    private String telecomProvider;
    private String telecomProviderId;
    private String msisdn;
    private String transactionType;
    private String currency;
    private BigDecimal amount;
    private BigDecimal txnCharge;
    private String status; // "PENDING", "FAILED", "SUCCESSFUL", "REVERSED"
    private String statusMessage;
    private String internalRef;
    private String remoteIp;
    private String paymentChanel;
    private String validatedAccountName;
    private String externalTransactionRef;
    private String createdAt;
    private String updatedAt;
}
