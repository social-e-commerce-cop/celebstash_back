package com.celebstash.backend.dto.artist;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ArtistApplicationRequest {
    @NotBlank(message = "Stage name is required")
    private String stageName;

    @NotBlank(message = "Category is required")
    private String category;

    private String bio;

    private String socialProofLink;
}
