package com.celebstash.backend.dto.concert;

import com.celebstash.backend.model.enums.TicketTier;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketPurchaseRequest {

    @NotNull(message = "Ticket tier is required")
    private TicketTier ticketTier;

    @NotBlank(message = "Wallet PIN is required")
    private String pin;
}
