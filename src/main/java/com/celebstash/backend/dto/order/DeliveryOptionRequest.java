package com.celebstash.backend.dto.order;

import com.celebstash.backend.model.enums.DeliveryOption;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryOptionRequest {
    
    @NotNull(message = "Delivery option is required")
    private DeliveryOption deliveryOption;
    
    // Location ID is required only if delivery option is DELIVERY
    private Long locationId;
    
    private String notes;
}