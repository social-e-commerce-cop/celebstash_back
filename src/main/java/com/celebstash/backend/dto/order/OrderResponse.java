package com.celebstash.backend.dto.order;

import com.celebstash.backend.dto.location.LocationResponse;
import com.celebstash.backend.model.enums.DeliveryOption;
import com.celebstash.backend.model.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderResponse {
    private Long id;
    private String orderNumber;
    private Long userId;
    private String userName;
    private OrderStatus status;
    private DeliveryOption deliveryOption;
    private LocationResponse location;
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal total;
    private String notes;
    private List<OrderItemResponse> items;
    private int itemCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime paidAt;
    private LocalDateTime completedAt;
    private boolean isPaid;
    private boolean isCompleted;
}