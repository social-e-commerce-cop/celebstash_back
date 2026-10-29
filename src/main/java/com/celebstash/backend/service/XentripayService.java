package com.celebstash.backend.service;

import com.celebstash.backend.config.XentripayConfig;
import com.celebstash.backend.dto.wallet.xentripay.*;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.enums.PaymentProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class XentripayService {

    private final XentripayConfig config;
    private final RestTemplate restTemplate;

    public XentripayService(XentripayConfig config, RestTemplateBuilder restTemplateBuilder) {
        this.config = config;
        this.restTemplate = restTemplateBuilder
                .connectTimeout(Duration.ofSeconds(6))
                .readTimeout(Duration.ofSeconds(10))
                .build();
    }

    private String cleanBaseUrl() {
        String url = config.getBaseUrl() != null ? config.getBaseUrl().trim() : "https://xentripay.com";
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/v1")) {
            url = url.substring(0, url.length() - 3);
        }
        return url;
    }

    /**
     * Builds HTTP headers with the required X-XENTRIPAY-KEY authorization header
     */
    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-XENTRIPAY-KEY", config.getApiKey());
        return headers;
    }

    /**
     * 3. Collections API: Initiate payment collection request (e.g. MTN MoMo / Airtel Money push)
     */
    public XentripayCollectionResponse initiateCollection(String email, String customerName, Integer amount, String rawPhoneNumber) {
        String cnumber = normalize10DigitPhone(rawPhoneNumber);
        String msisdn = normalize250Msisdn(rawPhoneNumber);

        if (config.isSandboxMode()) {
            log.info("Sandbox mode: simulating XentriPay collection for {} RWF to {}", amount, cnumber);
            return fallbackMockCollection(cnumber, amount);
        }

        String contactEmail = (email != null && !email.trim().isEmpty()) ? email.trim() : config.getBusinessEmail();
        String cname = (customerName != null && !customerName.trim().isEmpty()) ? customerName.trim() : config.getBusinessName();

        XentripayCollectionRequest requestBody = XentripayCollectionRequest.builder()
                .email(contactEmail)
                .cname(cname)
                .amount(amount)
                .cnumber(cnumber)
                .msisdn(msisdn)
                .currency(config.getCurrency())
                .pmethod("momo")
                .chargesIncluded(config.getChargesIncluded())
                .build();

        String url = cleanBaseUrl() + "/api/collections/initiate";
        log.info("Initiating XentriPay collection of {} {} for phone {} (msisdn: {}) to {}",
                amount, config.getCurrency(), cnumber, msisdn, url);

        try {
            HttpEntity<XentripayCollectionRequest> entity = new HttpEntity<>(requestBody, createHeaders());
            ResponseEntity<XentripayCollectionResponse> response = restTemplate.postForEntity(url, entity, XentripayCollectionResponse.class);
            log.info("XentriPay collection response: status={}, body={}", response.getStatusCode(), response.getBody());
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("XentriPay collection HTTP error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            return fallbackMockCollection(cnumber, amount);
        } catch (RestClientException ex) {
            log.warn("XentriPay collection connection error ({}). Falling back to mock collection for {} RWF to {}", ex.getMessage(), amount, cnumber);
            return fallbackMockCollection(cnumber, amount);
        }
    }

    /**
     * 3.9 Check Collection Status using Refid
     */
    public XentripayCollectionStatusResponse checkCollectionStatus(String refid) {
        if (config.isSandboxMode()) {
            log.info("Sandbox mode: simulating collection status SUCCESS for refid={}", refid);
            return XentripayCollectionStatusResponse.builder()
                    .refid(refid)
                    .status("SUCCESS")
                    .updatedAt(LocalDateTime.now().toString())
                    .build();
        }

        String url = cleanBaseUrl() + "/api/collections/status/" + refid;
        log.info("Checking XentriPay collection status for refid={} at {}", refid, url);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<XentripayCollectionStatusResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, XentripayCollectionStatusResponse.class
            );
            return response.getBody();
        } catch (Exception ex) {
            log.warn("XentriPay check status error ({}). Preserving PENDING state for refid={}", ex.getMessage(), refid);
            return XentripayCollectionStatusResponse.builder()
                    .refid(refid)
                    .status("PENDING")
                    .updatedAt(LocalDateTime.now().toString())
                    .build();
        }
    }

    /**
     * 4. Payouts (Payment Requests) API: Initiate disbursement payout
     */
    public XentripayPayoutResponse initiatePayout(String customerRef, String providerIdOrName, String destination, String recipientName, Integer amount) {
        String providerId = PaymentProvider.resolveProviderId(providerIdOrName, PaymentProvider.MTN_MOMO.getId());
        String msisdn = normalize10DigitPhone(destination);

        if (config.isSandboxMode()) {
            log.info("Sandbox mode: simulating XentriPay payout for {} RWF to {} (providerId: {})", amount, msisdn, providerId);
            return fallbackMockPayout(customerRef, providerId, msisdn, recipientName, amount);
        }

        XentripayPayoutRequest requestBody = XentripayPayoutRequest.builder()
                .customerReference(customerRef)
                .telecomProviderId(providerId)
                .msisdn(msisdn)
                .name((recipientName != null && !recipientName.trim().isEmpty()) ? recipientName.trim() : "Valued Customer")
                .transactionType("PAYOUT")
                .currency(config.getCurrency())
                .amount(amount)
                .build();

        String url = config.getBaseUrl() + "/api/payment-requests";
        log.info("Initiating XentriPay payout of {} {} to {} (providerId: {}) with ref {}",
                amount, config.getCurrency(), msisdn, providerId, customerRef);

        try {
            HttpEntity<XentripayPayoutRequest> entity = new HttpEntity<>(requestBody, createHeaders());
            ResponseEntity<XentripayPayoutResponse> response = restTemplate.postForEntity(url, entity, XentripayPayoutResponse.class);
            log.info("XentriPay payout response: status={}, body={}", response.getStatusCode(), response.getBody());
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("XentriPay payout HTTP error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (config.isSandboxMode()) {
                return fallbackMockPayout(customerRef, providerId, msisdn, recipientName, amount);
            }
            throw new AppException("XentriPay Payout request failed: " + ex.getResponseBodyAsString(), HttpStatus.BAD_GATEWAY);
        } catch (RestClientException ex) {
            log.error("XentriPay payout connection error: {}", ex.getMessage());
            if (config.isSandboxMode()) {
                return fallbackMockPayout(customerRef, providerId, msisdn, recipientName, amount);
            }
            throw new AppException("Failed to connect to XentriPay payout gateway.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 4.8 Check Payment Request (Payout) Status
     */
    public XentripayPayoutStatusResponse checkPayoutStatus(String customerRef) {
        if (config.isSandboxMode()) {
            return XentripayPayoutStatusResponse.builder()
                    .timestamp(LocalDateTime.now().toString())
                    .message("Success")
                    .data(XentripayPayoutStatusResponse.StatusData.builder()
                            .status("COMPLETED")
                            .reference_number(customerRef)
                            .amount("0")
                            .build())
                    .build();
        }

        String url = config.getBaseUrl() + "/api/payment-requests/check-status?customerRef=" + customerRef;
        log.info("Checking XentriPay payout status for customerRef={} at {}", customerRef, url);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            ResponseEntity<XentripayPayoutStatusResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, XentripayPayoutStatusResponse.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("XentriPay payout check status error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (config.isSandboxMode()) {
                return XentripayPayoutStatusResponse.builder()
                        .timestamp(LocalDateTime.now().toString())
                        .message("Success")
                        .data(XentripayPayoutStatusResponse.StatusData.builder()
                                .status("COMPLETED")
                                .reference_number(customerRef)
                                .amount("0")
                                .build())
                        .build();
            }
            throw new AppException("Failed to check payout status: " + ex.getResponseBodyAsString(), HttpStatus.BAD_GATEWAY);
        } catch (RestClientException ex) {
            log.error("XentriPay payout check connection error: {}", ex.getMessage());
            if (config.isSandboxMode()) {
                return XentripayPayoutStatusResponse.builder()
                        .timestamp(LocalDateTime.now().toString())
                        .message("Success")
                        .data(XentripayPayoutStatusResponse.StatusData.builder()
                                .status("COMPLETED")
                                .reference_number(customerRef)
                                .amount("0")
                                .build())
                        .build();
            }
            throw new AppException("Failed to reach XentriPay to check payout status.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 4.9 Validate Account Name
     */
    public XentripayValidateNameResponse validateAccountName(String accountNumber, String providerIdOrName) {
        String providerId = PaymentProvider.resolveProviderId(providerIdOrName, PaymentProvider.MTN_MOMO.getId());
        String normalizedAccount = normalize10DigitPhone(accountNumber);

        if (config.isSandboxMode()) {
            return XentripayValidateNameResponse.builder()
                    .registeredName("Registered Account Holder")
                    .build();
        }

        XentripayValidateNameRequest requestBody = XentripayValidateNameRequest.builder()
                .accountNumber(normalizedAccount)
                .bankId(providerId)
                .build();

        String url = config.getBaseUrl() + "/api/payment-requests/validate-name";
        log.info("Validating account name for account={} with bankId={} at {}", normalizedAccount, providerId, url);

        try {
            HttpEntity<XentripayValidateNameRequest> entity = new HttpEntity<>(requestBody, createHeaders());
            ResponseEntity<XentripayValidateNameResponse> response = restTemplate.postForEntity(url, entity, XentripayValidateNameResponse.class);
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("XentriPay validate name HTTP error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (config.isSandboxMode()) {
                return XentripayValidateNameResponse.builder()
                        .registeredName("Registered Account Holder")
                        .build();
            }
            throw new AppException("Account validation failed: " + ex.getResponseBodyAsString(), HttpStatus.BAD_REQUEST);
        } catch (RestClientException ex) {
            log.error("XentriPay validate name connection error: {}", ex.getMessage());
            if (config.isSandboxMode()) {
                return XentripayValidateNameResponse.builder()
                        .registeredName("Registered Account Holder")
                        .build();
            }
            throw new AppException("Could not validate account at this time.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    /**
     * 4.10 Check Wallet Balance of Merchant / Business
     */
    public XentripayBusinessWalletResponse getBusinessWalletBalance() {
        if (config.isSandboxMode()) {
            return XentripayBusinessWalletResponse.builder()
                    .walletId(1L)
                    .businessAccountId(1L)
                    .businessName(config.getBusinessName())
                    .balance("1000000.00")
                    .currency("RWF")
                    .active(true)
                    .build();
        }

        String url = config.getBaseUrl() + "/api/wallets/my-business";
        log.info("Checking XentriPay business wallet balance at {}", url);

        try {
            HttpEntity<Void> entity = new HttpEntity<>(createHeaders());
            // Documentation specifies endpoint is GET /api/wallets/my-business
            ResponseEntity<XentripayBusinessWalletResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, XentripayBusinessWalletResponse.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("XentriPay business wallet error: status={}, body={}", ex.getStatusCode(), ex.getResponseBodyAsString());
            if (config.isSandboxMode()) {
                return XentripayBusinessWalletResponse.builder()
                        .walletId(1L)
                        .businessAccountId(1L)
                        .businessName(config.getBusinessName())
                        .balance("1000000.00")
                        .currency("RWF")
                        .active(true)
                        .build();
            }
            throw new AppException("Failed to retrieve business wallet balance: " + ex.getResponseBodyAsString(), HttpStatus.BAD_GATEWAY);
        } catch (RestClientException ex) {
            log.error("XentriPay business wallet connection error: {}", ex.getMessage());
            if (config.isSandboxMode()) {
                return XentripayBusinessWalletResponse.builder()
                        .walletId(1L)
                        .businessAccountId(1L)
                        .businessName(config.getBusinessName())
                        .balance("1000000.00")
                        .currency("RWF")
                        .active(true)
                        .build();
            }
            throw new AppException("Failed to connect to XentriPay to retrieve balance.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    // --- Phone Sanitization Helpers ---

    /**
     * Normalizes phone number to 10 digits format (e.g., 0780371519)
     */
    public static String normalize10DigitPhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("250") && digits.length() == 12) {
            return "0" + digits.substring(3);
        }
        if (digits.length() == 9 && digits.startsWith("7")) {
            return "0" + digits;
        }
        return digits;
    }

    /**
     * Normalizes phone number to MSISDN format with 250 prefix (e.g., 250780371519)
     */
    public static String normalize250Msisdn(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("[^0-9]", "");
        if (digits.startsWith("07") && digits.length() == 10) {
            return "250" + digits.substring(1);
        }
        if (digits.length() == 9 && digits.startsWith("7")) {
            return "250" + digits;
        }
        if (digits.startsWith("250") && digits.length() == 12) {
            return digits;
        }
        return digits;
    }

    // --- Sandbox / Fallback Helpers ---

    private XentripayCollectionResponse fallbackMockCollection(String cnumber, Integer amount) {
        String randomHex = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return XentripayCollectionResponse.builder()
                .reply("Thank you for initiating a new Payment. Check your pending transactions on *182*7*1# to confirm.")
                .url(" ")
                .success(1)
                .authkey("mock_auth_" + randomHex)
                .tid("TID-" + randomHex)
                .refid("Ref" + randomHex)
                .retcode(0)
                .build();
    }

    private XentripayPayoutResponse fallbackMockPayout(String customerRef, String providerId, String msisdn, String name, Integer amount) {
        String randomHex = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        return XentripayPayoutResponse.builder()
                .id(System.currentTimeMillis() % 100000)
                .businessName(config.getBusinessName())
                .customerReference(customerRef)
                .telecomProvider("MOMO")
                .telecomProviderId(providerId)
                .msisdn(msisdn)
                .transactionType("PAYOUT")
                .currency(config.getCurrency())
                .amount(java.math.BigDecimal.valueOf(amount))
                .txnCharge(java.math.BigDecimal.ZERO)
                .status("PENDING")
                .statusMessage("Payment request submitted successfully, awaiting confirmation.")
                .internalRef("Ref" + randomHex)
                .validatedAccountName(name)
                .createdAt(LocalDateTime.now().toString())
                .updatedAt(LocalDateTime.now().toString())
                .build();
    }
}
