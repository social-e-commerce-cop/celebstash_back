package com.celebstash.backend.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "xentripay")
public class XentripayConfig {

    /**
     * API Key supplied in header: X-XENTRIPAY-KEY
     */
    private String apiKey = "vzimHGg2_iXcmwpwxdwy_KKW5zMr3_iV";

    /**
     * Test URL: https://test.xentripay.com
     * Production URL: https://xentripay.com
     */
    private String baseUrl = "https://test.xentripay.com";

    /**
     * Default business email for collection requests
     */
    private String businessEmail = "info@celebstash.com";

    /**
     * Default business/merchant name
     */
    private String businessName = "CelebStash";

    /**
     * Currency code (Must be RWF)
     */
    private String currency = "RWF";

    /**
     * Whether charges are included in collection
     */
    private String chargesIncluded = "true";

    /**
     * If true or when API key is missing/unreachable, sandbox/mock simulation is used
     */
    private boolean sandboxMode = false;
}
