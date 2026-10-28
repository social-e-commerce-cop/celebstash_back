package com.celebstash.backend.dto.wallet.xentripay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XentripayPayoutStatusResponse {
    private String timestamp;
    private String message;
    private StatusData data;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StatusData {
        private String status; // "COMPLETED", "PENDING", "FAILED"
        private String reference_number;
        private String amount;
    }
}
