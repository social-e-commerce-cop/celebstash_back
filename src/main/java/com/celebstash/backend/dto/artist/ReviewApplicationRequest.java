package com.celebstash.backend.dto.artist;

import lombok.Data;

@Data
public class ReviewApplicationRequest {
    private boolean approve;
    private String rejectionReason;
}
