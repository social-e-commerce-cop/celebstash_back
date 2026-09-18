package com.celebstash.backend.dto.music;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseAccessDto {
    private Long releaseId;
    private String pin;
    private boolean isGift;
    private Long giftRecipientId;
    private String giftRecipientUsername;
    private String giftMessage;
    private BigDecimal price;
}
