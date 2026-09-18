package com.celebstash.backend.dto.kyc;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycSubmitRequest {
    @NotBlank(message = "ID document URL is required")
    private String idDocumentUrl;
    
    private String socialMediaLink;
}
