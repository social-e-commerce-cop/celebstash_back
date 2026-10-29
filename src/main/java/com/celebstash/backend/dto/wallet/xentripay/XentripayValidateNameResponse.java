package com.celebstash.backend.dto.wallet.xentripay;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class XentripayValidateNameResponse {
    private String registeredName;
}
