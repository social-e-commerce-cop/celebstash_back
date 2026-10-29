package com.celebstash.backend;

import com.celebstash.backend.config.XentripayConfig;
import com.celebstash.backend.dto.wallet.xentripay.*;
import com.celebstash.backend.model.enums.PaymentProvider;
import com.celebstash.backend.service.XentripayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.client.RestTemplateBuilder;

import static org.junit.jupiter.api.Assertions.*;

public class XentripayWalletTest {

    private XentripayConfig config;
    private XentripayService xentripayService;

    @BeforeEach
    void setUp() {
        config = new XentripayConfig();
        config.setApiKey("test-key");
        config.setBaseUrl("https://test.xentripay.com");
        config.setSandboxMode(true); // Enable sandbox mock for testing
        xentripayService = new XentripayService(config, new RestTemplateBuilder());
    }

    @Test
    void testPaymentProviderResolution() {
        // Mobile Money
        assertEquals("63510", PaymentProvider.resolveProviderId("MTN", ""));
        assertEquals("63510", PaymentProvider.resolveProviderId("MTN_MOMO", ""));
        assertEquals("63510", PaymentProvider.resolveProviderId("63510", ""));
        assertEquals("63514", PaymentProvider.resolveProviderId("AIRTEL", ""));
        assertEquals("63514", PaymentProvider.resolveProviderId("AIRTEL RWANDA", ""));
        assertEquals("63509", PaymentProvider.resolveProviderId("SPENN", ""));

        // Banks
        assertEquals("040", PaymentProvider.resolveProviderId("BK", ""));
        assertEquals("040", PaymentProvider.resolveProviderId("BANQUE DE KIGALI", ""));
        assertEquals("192", PaymentProvider.resolveProviderId("EQUITY", ""));
        assertEquals("100", PaymentProvider.resolveProviderId("ECOBANK", ""));
        assertEquals("400", PaymentProvider.resolveProviderId("BPR", ""));
        assertEquals("010", PaymentProvider.resolveProviderId("I&M", ""));
        assertEquals("800", PaymentProvider.resolveProviderId("ZIGAMA", ""));
    }

    @Test
    void testPhoneNumberNormalization() {
        // 10 digits format (07XXXXXXXX)
        assertEquals("0780371519", XentripayService.normalize10DigitPhone("0780371519"));
        assertEquals("0780371519", XentripayService.normalize10DigitPhone("250780371519"));
        assertEquals("0780371519", XentripayService.normalize10DigitPhone("+250780371519"));
        assertEquals("0780371519", XentripayService.normalize10DigitPhone("078 037 1519"));
        assertEquals("0780371519", XentripayService.normalize10DigitPhone("780371519"));

        // MSISDN format with 250 prefix (2507XXXXXXXX)
        assertEquals("250780371519", XentripayService.normalize250Msisdn("0780371519"));
        assertEquals("250780371519", XentripayService.normalize250Msisdn("250780371519"));
        assertEquals("250780371519", XentripayService.normalize250Msisdn("+250780371519"));
        assertEquals("250780371519", XentripayService.normalize250Msisdn("780371519"));
    }

    @Test
    void testCollectionInitiationInSandbox() {
        XentripayCollectionResponse response = xentripayService.initiateCollection(
                "buyer@celebstash.com", "Test Buyer", 1000, "0780371519"
        );

        assertNotNull(response);
        assertEquals(1, response.getSuccess());
        assertNotNull(response.getRefid());
        assertTrue(response.getRefid().startsWith("Ref"));
        assertTrue(response.getReply().contains("*182*7*1#"));
    }

    @Test
    void testCollectionStatusCheckInSandbox() {
        XentripayCollectionStatusResponse response = xentripayService.checkCollectionStatus("RefE1108D491A1C");
        assertNotNull(response);
        assertEquals("RefE1108D491A1C", response.getRefid());
        assertEquals("SUCCESS", response.getStatus());
    }

    @Test
    void testPayoutInitiationInSandbox() {
        XentripayPayoutResponse response = xentripayService.initiatePayout(
                "TXN-TEST-001", "MTN", "0795876908", "RUKUNDO Jean de Dieu", 500
        );

        assertNotNull(response);
        assertEquals("TXN-TEST-001", response.getCustomerReference());
        assertEquals("PENDING", response.getStatus());
        assertEquals("RWF", response.getCurrency());
        assertNotNull(response.getInternalRef());
    }

    @Test
    void testValidateAccountNameInSandbox() {
        XentripayValidateNameResponse response = xentripayService.validateAccountName(
                "0795876908", "63510"
        );

        assertNotNull(response);
        assertNotNull(response.getRegisteredName());
    }

    @Test
    void testBusinessWalletBalanceInSandbox() {
        XentripayBusinessWalletResponse response = xentripayService.getBusinessWalletBalance();

        assertNotNull(response);
        assertEquals("RWF", response.getCurrency());
        assertTrue(response.getActive());
    }
}
