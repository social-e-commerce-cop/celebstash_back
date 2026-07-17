package com.celebstash.backend.dto.concert;

import com.celebstash.backend.model.enums.TicketTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketResponse {
    private Long id;
    private Long concertId;
    private String concertName;
    private LocalDateTime concertDate;
    private String venue;
    private Long userId;
    private String userName;
    private TicketTier ticketTier;
    private String verificationCode;
    private boolean isUsed;
    private LocalDateTime purchasedAt;
}
