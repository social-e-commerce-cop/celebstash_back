package com.celebstash.backend.model.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

@Getter
public enum PaymentProvider {

    // Mobile Money Providers
    MTN_MOMO("63510", "MTN MOBILE MONEY", "MTN Mobile Money", true),
    AIRTEL_RWANDA("63514", "AIRTEL RWANDA", "Airtel Money", true),
    SPENN("63509", "SPENN", "SPENN", true),

    // Banks
    BANQUE_DE_KIGALI("040", "BANQUE DE KIGALI", "Bank of Kigali (BK)", false),
    EQUITY_BANK("192", "EQUITY BANK", "Equity Bank", false),
    ECOBANK_RWANDA("100", "ECOBANK RWANDA", "Ecobank Rwanda", false),
    ACCESS_BANK("115", "ACCESS BANK RWANDA", "Access Bank", false),
    BPR("400", "BANQUE POPULAIRE DU RWANDA", "BPR Bank", false),
    IM_BANK("010", "INVESTMENT AND MORTGAGE BANK", "I&M Bank", false),
    GT_BANK("070", "GUARANTY TRUST BANK (RWANDA)", "GT Bank", false),
    NCBA("025", "NATIONAL COMMERCIAL BANK OF AFRICA", "NCBA Bank", false),
    URWEGO_BANK("145", "URWEGO OPPORTUNITY BANK", "Urwego Bank", false),
    ZIGAMA_CSS("800", "ZIGAMA CREDIT AND SAVINGS SCHEME", "Zigama CSS", false),
    BANK_OF_AFRICA("900", "BANK OF AFRICA RWANDA", "Bank of Africa", false),
    UNGUKA_BANK("950", "UNGUKA BANK", "Unguka Bank", false),
    BNR("951", "BANQUE NATIONALE DU RWANDA", "National Bank of Rwanda", false);

    private final String id;
    private final String officialName;
    private final String displayName;
    private final boolean isMobileMoney;

    PaymentProvider(String id, String officialName, String displayName, boolean isMobileMoney) {
        this.id = id;
        this.officialName = officialName;
        this.displayName = displayName;
        this.isMobileMoney = isMobileMoney;
    }

    /**
     * Resolves provider by ID, enum name, or partial match
     */
    public static Optional<PaymentProvider> find(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Optional.empty();
        }
        String clean = query.trim().toUpperCase().replace("-", "_").replace(" ", "_");

        // Try exact match on ID
        for (PaymentProvider p : values()) {
            if (p.getId().equalsIgnoreCase(query.trim())) {
                return Optional.of(p);
            }
        }

        // Try exact match on enum name
        for (PaymentProvider p : values()) {
            if (p.name().equalsIgnoreCase(clean)) {
                return Optional.of(p);
            }
        }

        // Try match on official or display name
        for (PaymentProvider p : values()) {
            if (p.getOfficialName().equalsIgnoreCase(query.trim()) ||
                p.getDisplayName().equalsIgnoreCase(query.trim())) {
                return Optional.of(p);
            }
        }

        // Loose keyword matching
        if (clean.contains("MTN") || clean.contains("MOMO")) {
            return Optional.of(MTN_MOMO);
        }
        if (clean.contains("AIRTEL")) {
            return Optional.of(AIRTEL_RWANDA);
        }
        if (clean.contains("SPENN")) {
            return Optional.of(SPENN);
        }
        if (clean.contains("KIGALI") || clean.contains("BK")) {
            return Optional.of(BANQUE_DE_KIGALI);
        }
        if (clean.contains("EQUITY")) {
            return Optional.of(EQUITY_BANK);
        }
        if (clean.contains("ECOBANK")) {
            return Optional.of(ECOBANK_RWANDA);
        }
        if (clean.contains("BPR") || clean.contains("POPULAIRE")) {
            return Optional.of(BPR);
        }
        if (clean.contains("ACCESS")) {
            return Optional.of(ACCESS_BANK);
        }
        if (clean.contains("I&M") || clean.contains("MORTGAGE") || clean.contains("IM")) {
            return Optional.of(IM_BANK);
        }
        if (clean.contains("ZIGAMA")) {
            return Optional.of(ZIGAMA_CSS);
        }

        return Optional.empty();
    }

    public static String resolveProviderId(String query, String defaultId) {
        return find(query).map(PaymentProvider::getId).orElse(defaultId != null ? defaultId : MTN_MOMO.getId());
    }
}
