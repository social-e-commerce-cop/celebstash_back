package com.celebstash.backend.dto.post;

import com.celebstash.backend.dto.comment.CommentResponse;
import com.celebstash.backend.dto.product.ProductResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {

    private Long id;
    private Long userId;
    private String userName;
    private String userImageUrl;
    private String userRole;
    private boolean userVerified;
    private ProductResponse product;
    private String videoUrl;
    private List<String> imageUrls;
    private String description;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // Counts (changed from int -> long)
    private long likesCount;
    private long commentsCount;
    private long sharesCount;
    private long repostsCount;

    // User interactions
    private boolean isLiked;
    private boolean isShared;
    private boolean isReposted;
    private boolean isSaved;

    // Comments (optional, may be loaded separately)
    private List<CommentResponse> comments;

    private boolean isSponsored;
    private String sponsorName;

    // Attached Shoppable Item fields
    private String attachedType;
    private String attachedTitle;
    private String attachedSubtitle;
    private String attachedPrice;
}
