package com.celebstash.backend.dto.post;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateRequest {

    @Size(max = 1000, message = "Description cannot exceed 1000 characters")
    private String description;

    @Size(min = 3, max = 5, message = "Post must have 3-5 images")
    private List<MultipartFile> images;

    private MultipartFile video;

    // Optional product ID if the post is for a product
    private Long productId;
}