package com.celebstash.backend.dto.wallet;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.50", message = "Minimum transfer is $0.50")
    private BigDecimal amount;

    @NotBlank(message = "Recipient username or email is required")
    private String recipient;

    private String note;

    private String pin;
}
