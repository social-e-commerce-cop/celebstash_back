package com.celebstash.backend.dto.concert;

import jakarta.validation.constraints.Future;
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
public class ConcertRequest {

    @NotBlank(message = "Event name is required")
    private String name;

    private String description;

    @NotNull(message = "Event date and time is required")
    @Future(message = "Event date must be in the future")
    private LocalDateTime date;

    @NotBlank(message = "Venue is required")
    private String venue;

    @NotNull(message = "General ticket price is required")
    @Min(value = 0, message = "Ticket price must be positive")
    private BigDecimal ticketPriceGeneral;

    @NotNull(message = "VIP ticket price is required")
    @Min(value = 0, message = "Ticket price must be positive")
    private BigDecimal ticketPriceVip;

    @NotNull(message = "Capacity is required")
    @Min(value = 1, message = "Capacity must be at least 1")
    private Integer capacity;

    private String coverImage;
}
