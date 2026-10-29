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
public class MomoTopUpInitResponse {
    // Wallet Response compatibility fields
    private Long id;
    private Long userId;
    private String userName;
    private BigDecimal balance;
    private BigDecimal heldBalance;
    private boolean hasPinSet;

    // XentriPay Collection metadata
    private boolean success;
    private String refid;
    private String tid;
    private String instructions;
    private String status; // "PENDING", "COMPLETED"

    private WalletResponse wallet;
    private TransactionResponse transaction;
}
