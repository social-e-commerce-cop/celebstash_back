package com.celebstash.backend.dto.kyc;

import com.celebstash.backend.model.enums.KycStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycResponse {
    private Long id;
    private Long userId;
    private String userName;
    private String userEmail;
    private String idDocumentUrl;
    private String socialMediaLink;
    private KycStatus status;
    private String adminNotes;
    private LocalDateTime createdAt;
    private LocalDateTime reviewedAt;
    private String reviewedByName;
}
