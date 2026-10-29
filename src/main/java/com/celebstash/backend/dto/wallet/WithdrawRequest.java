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
public class WithdrawRequest {
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "1.00", message = "Minimum withdrawal is $1.00")
    private BigDecimal amount;

    @NotBlank(message = "Destination phone or account number is required")
    private String destination;

    private String provider; // e.g. "MTN", "Airtel", "Bank"

    private String pin;
}
