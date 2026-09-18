package com.celebstash.backend.dto.premium;

import com.celebstash.backend.model.enums.PremiumAccessModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PremiumContentResponse {
    private Long id;
    private Long creatorId;
    private String creatorName;
    private String title;
    private String description;
    private String coverImageUrl;
    private PremiumAccessModel accessModel;
    private BigDecimal price;
    private LocalDateTime earlyAccessUntil;
    private LocalDateTime createdAt;
    private boolean isUnlocked;
    private Integer remainingPlays;
    private Integer playCap;
}
