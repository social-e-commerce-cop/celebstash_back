package com.celebstash.backend.dto.wallet.xentripay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XentripayCollectionRequest {
    private String email;
    private String cname;
    private Integer amount;
    private String cnumber;
    private String msisdn;
    private String currency;
    private String pmethod;
    private String chargesIncluded;
}
