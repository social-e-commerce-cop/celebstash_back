package com.celebstash.backend.model.enums;

/**
 * Enum representing the delivery options for an order
 */
public enum DeliveryOption {
    SELF_PICKUP,    // Customer picks up the order themselves (no delivery fee)
    DELIVERY        // Order is delivered to the customer's location (delivery fee applies)
}