package com.celebstash.backend.service;

import com.celebstash.backend.dto.wallet.xentripay.XentripayCollectionResponse;
import com.celebstash.backend.dto.wallet.xentripay.XentripayPayoutResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class MomoGatewayService {

    private final XentripayService xentripayService;

    /**
     * Initiates Mobile Money collection payment request (MTN MoMo or Airtel Money) via XentriPay
     */
    public MomoTransactionResponse initiatePayment(String email, String customerName, String phoneNumber, BigDecimal amount, String provider) {
        log.info("Initiating Mobile Money collection via XentriPay: amount={}, phone={}, provider={}", amount, phoneNumber, provider);
        Integer intAmount = amount.setScale(0, RoundingMode.HALF_UP).intValue();

        try {
            XentripayCollectionResponse response = xentripayService.initiateCollection(email, customerName, intAmount, phoneNumber);
            String refid = response.getRefid() != null ? response.getRefid() : "MOMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

            // Mobile money collection pushes ALWAYS require user authorization on their handset.
            // Initial state is strictly PENDING. Wallet balance is NEVER credited here.
            return MomoTransactionResponse.builder()
                    .transactionId(refid)
                    .externalRef(response.getTid())
                    .status("PENDING")
                    .message(response.getReply() != null ? response.getReply() : "Payment request submitted. Please confirm on your mobile phone.")
                    .build();
        } catch (Exception ex) {
            log.warn("XentriPay direct call error, generating gateway reference: {}", ex.getMessage());
            String ref = "MOMO-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            return MomoTransactionResponse.builder()
                    .transactionId(ref)
                    .status("PENDING")
                    .message("Payment initiated: " + ex.getMessage())
                    .build();
        }
    }

    /**
     * Overload for backward compatibility
     */
    public MomoTransactionResponse initiatePayment(String phoneNumber, BigDecimal amount, String provider) {
        return initiatePayment(null, null, phoneNumber, amount, provider);
    }

    /**
     * Initiates disbursement payout out to Mobile Money or Bank destination via XentriPay
     */
    public MomoTransactionResponse initiateDisbursement(String customerRef, String destination, BigDecimal amount, String provider, String recipientName) {
        log.info("Initiating disbursement payout via XentriPay: ref={}, amount={}, destination={}, provider={}",
                customerRef, amount, destination, provider);
        Integer intAmount = amount.setScale(0, RoundingMode.HALF_UP).intValue();

        try {
            XentripayPayoutResponse response = xentripayService.initiatePayout(customerRef, provider, destination, recipientName, intAmount);
            String status = response.getStatus() != null ? response.getStatus() : "PENDING";
            return MomoTransactionResponse.builder()
                    .transactionId(response.getCustomerReference() != null ? response.getCustomerReference() : customerRef)
                    .externalRef(response.getInternalRef())
                    .status(status)
                    .message(response.getStatusMessage() != null ? response.getStatusMessage() : "Disbursement request submitted.")
                    .build();
        } catch (Exception ex) {
            log.warn("XentriPay disbursement error: {}", ex.getMessage());
            return MomoTransactionResponse.builder()
                    .transactionId(customerRef)
                    .status("FAILED")
                    .message("Disbursement error: " + ex.getMessage())
                    .build();
        }
    }

    /**
     * Overload for backward compatibility
     */
    public MomoTransactionResponse initiateDisbursement(String destination, BigDecimal amount, String provider) {
        String ref = "PAYOUT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return initiateDisbursement(ref, destination, amount, provider, "Recipient");
    }

    @lombok.Data
    @lombok.Builder
    public static class MomoTransactionResponse {
        private String transactionId;
        private String externalRef;
        private String status;
        private String message;
    }
}
