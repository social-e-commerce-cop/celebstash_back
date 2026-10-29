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
public class XentripayBusinessWalletResponse {
    private Long walletId;
    private Long businessAccountId;
    private String businessName;
    private String balance;
    private String currency;
    private Boolean active;
}
