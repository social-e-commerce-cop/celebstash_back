package com.celebstash.backend.dto.wallet.xentripay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderInfoResponse {
    private String id;
    private String code;
    private String name;
    private String displayName;
    private boolean isMobileMoney;
}
