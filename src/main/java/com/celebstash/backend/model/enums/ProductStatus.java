package com.celebstash.backend.model.enums;

public enum ProductStatus {
    PENDING,    // Initial state, waiting for admin approval
    APPROVED,   // Approved by admin, visible to all users
    REJECTED,   // Rejected by admin, not visible to users
    RESERVED,   // In a user's cart with reservation hold active
    SOLD_OUT,   // Stock exhausted or auction completed
    IN_BIDDING  // Product moved to bidding section
}