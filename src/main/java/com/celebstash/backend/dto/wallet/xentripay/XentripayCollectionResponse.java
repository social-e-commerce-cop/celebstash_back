package com.celebstash.backend.dto.wallet.xentripay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XentripayCollectionResponse {
    private String reply;
    private String url;
    private Integer success;
    private String authkey;
    private String tid;
    private String refid;
    private Integer retcode;
}
