package com.celebstash.backend.dto.wallet.xentripay;

import com.celebstash.backend.dto.wallet.TransactionResponse;
import com.celebstash.backend.dto.wallet.WalletResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionVerificationResponse {
    // Wallet Response compatibility fields
    private Long id;
    private Long userId;
    private String userName;
    private BigDecimal balance;
    private BigDecimal heldBalance;
    private boolean hasPinSet;

    // Status details
    private String status; // "PENDING", "COMPLETED", "FAILED"
    private String message;
    private String reference;
    private String externalReference;
    private BigDecimal amount;
    private BigDecimal beforeBalance;
    private BigDecimal afterBalance;

    private WalletResponse wallet;
    private TransactionResponse transaction;
}
