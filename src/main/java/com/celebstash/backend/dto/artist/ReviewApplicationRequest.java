package com.celebstash.backend.dto.artist;

import lombok.Data;

@Data
public class ReviewApplicationRequest {
    private boolean approve;
    private String status;
    private String rejectionReason;
    private String reviewNotes;

    public boolean isApproved() {
        if ("APPROVED".equalsIgnoreCase(status)) return true;
        if ("REJECTED".equalsIgnoreCase(status)) return false;
        return approve;
    }
}
