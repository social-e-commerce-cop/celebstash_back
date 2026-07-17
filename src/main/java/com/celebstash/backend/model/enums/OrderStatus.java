package com.celebstash.backend.model.enums;

/**
 * Enum representing the status of an order
 */
public enum OrderStatus {
    PENDING_PAYMENT,    // Order created but not yet paid
    PAID,               // Order has been paid
    PROCESSING,         // Order is being processed
    READY_FOR_PICKUP,   // Order is ready for pickup (for SELF_PICKUP)
    OUT_FOR_DELIVERY,   // Order is out for delivery (for DELIVERY)
    DELIVERED,          // Order has been delivered
    COMPLETED,          // Order has been completed
    CANCELLED           // Order has been cancelled
}