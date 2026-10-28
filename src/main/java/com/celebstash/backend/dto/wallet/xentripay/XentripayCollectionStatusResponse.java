package com.celebstash.backend.dto.wallet.xentripay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XentripayCollectionStatusResponse {
    private String refid;
    private String status; // "SUCCESS", "PENDING", "FAILED"
    private String updatedAt;
}
