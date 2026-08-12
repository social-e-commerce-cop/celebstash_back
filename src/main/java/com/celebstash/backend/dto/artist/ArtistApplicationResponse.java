package com.celebstash.backend.dto.artist;

import com.celebstash.backend.model.enums.ApplicationStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class ArtistApplicationResponse {
    private Long id;
    private Long userId;
    private String userFullName;
    private String userEmail;
    private String stageName;
    private String category;
    private String bio;
    private String socialProofLink;
    private ApplicationStatus status;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;
}
