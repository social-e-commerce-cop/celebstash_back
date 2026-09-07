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

    @Size(max = 5000, message = "Description cannot exceed 5000 characters")
    private String description;

    @Size(max = 10, message = "Post cannot exceed 10 images")
    private List<String> imageUrls;

    private String videoUrl;

    // Optional product ID if the post is for a product
    private Long productId;

    private boolean isSponsored;
    private String sponsorName;

    // Attached Shoppable Item fields
    private String attachedType;
    private String attachedTitle;
    private String attachedSubtitle;
    private String attachedPrice;
}