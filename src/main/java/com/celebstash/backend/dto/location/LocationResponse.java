package com.celebstash.backend.dto.location;

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
public class LocationResponse {
    private Long id;
    private String name;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String country;
    private BigDecimal deliveryFee;
    private Boolean active;
    private Long userId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}