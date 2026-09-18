package com.celebstash.backend.dto.kyc;

import com.celebstash.backend.model.enums.KycStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycReviewRequest {
    @NotNull(message = "Review status is required")
    private KycStatus status;
    
    private String adminNotes;
}
