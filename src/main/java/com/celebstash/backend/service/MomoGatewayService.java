package com.celebstash.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class MomoGatewayService {

    /**
     * Simulates initiating a Mobile Money push transaction (MTN MoMo or Airtel Money)
     */
    public MomoTransactionResponse initiatePayment(String phoneNumber, BigDecimal amount, String provider) {
        log.info("Initiating Mobile Money push of ${} to {} via {}", amount, phoneNumber, provider);
        
        // Generate a mock gateway transaction reference
        String transactionId = "MOMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        
        return MomoTransactionResponse.builder()
                .transactionId(transactionId)
                .status("SUCCESS") // Simulate instant user approval in the mock
                .message("Push request completed successfully")
                .build();
    }

    @lombok.Data
    @lombok.Builder
    public static class MomoTransactionResponse {
        private String transactionId;
        private String status;
        private String message;
    }
}
