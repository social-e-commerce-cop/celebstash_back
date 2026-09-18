package com.celebstash.backend.dto.music;

import com.celebstash.backend.model.enums.BenefitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseBenefitDto {
    private Long id;
    private BenefitType benefitType;
    private boolean enabled;
    private String customName;
    private String customDescription;
    private String configData;
}
