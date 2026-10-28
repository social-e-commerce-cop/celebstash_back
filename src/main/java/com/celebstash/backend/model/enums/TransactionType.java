package com.celebstash.backend.model.enums;

/**
 * Enum representing the type of transaction
 */
public enum TransactionType {
    DEPOSIT,        // Adding money to wallet
    PURCHASE,       // Buying a product or music access
    BID,            // Placing a bid
    BID_REFUND,     // Refund for outbid or unsuccessful bid
    PAYMENT,        // Direct wallet payment for orders
    WITHDRAWAL,     // Cash out to Mobile Money or Bank
    TRANSFER_IN,    // Received funds from another user
    TRANSFER_OUT    // Sent funds to another user
}