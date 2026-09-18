package com.celebstash.backend.dto.premium;

import com.celebstash.backend.model.enums.PremiumAccessModel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
public class PremiumContentRequest {
    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    private String coverImageUrl;

    @NotNull(message = "Access model is required")
    private PremiumAccessModel accessModel;

    @NotNull(message = "Price is required")
    @Min(value = 0, message = "Price must be at least 0")
    private BigDecimal price;

    private LocalDateTime earlyAccessUntil;
}
