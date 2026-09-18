package com.celebstash.backend.dto.concert;

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
public class ConcertResponse {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime date;
    private String venue;
    private BigDecimal ticketPriceGeneral;
    private BigDecimal ticketPriceVip;
    private Integer capacity;
    private Integer availableTickets;
    private String coverImage;
    private Long sellerId;
    private String sellerName;
    private LocalDateTime createdAt;
}
