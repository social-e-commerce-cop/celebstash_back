package com.celebstash.backend.dto.post;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostRequest {

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Size(min = 3, max = 5, message = "Post must have 3-5 images")
    private List<String> imageUrls;

    private String videoUrl;

    // Optional product ID if the post is for a product
    private Long productId;

    private boolean isSponsored;
    private String sponsorName;
}