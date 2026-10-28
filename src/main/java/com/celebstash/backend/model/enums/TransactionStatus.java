package com.celebstash.backend.model.enums;

/**
 * Enum representing the status of a transaction
 */
public enum TransactionStatus {
    PENDING,    // Transaction is awaiting user authorization on phone/gateway
    PROCESSING, // Transaction is actively being processed by provider
    COMPLETED,  // Transaction has been completed successfully and wallet credited
    FAILED,     // Transaction has failed or was rejected
    CANCELLED,  // Transaction was cancelled by user
    REFUNDED    // Transaction has been refunded
}