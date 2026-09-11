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

    // User interactions.
    // The fields drop the "is" prefix so that Lombok's isLiked()/isSaved() getters resolve to
    // the same Jackson property as the field, and @JsonProperty then names it once on the wire.
    // Clients read "isLiked"/"isSaved"; naming the fields isLiked/isSaved instead would publish
    // both "liked" and "isLiked" for the same value.
    @com.fasterxml.jackson.annotation.JsonProperty("isLiked")
    private boolean liked;
    @com.fasterxml.jackson.annotation.JsonProperty("isShared")
    private boolean shared;
    @com.fasterxml.jackson.annotation.JsonProperty("isReposted")
    private boolean reposted;
    @com.fasterxml.jackson.annotation.JsonProperty("isSaved")
    private boolean saved;

    // Comments (optional, may be loaded separately)
    private List<CommentResponse> comments;

    @com.fasterxml.jackson.annotation.JsonProperty("isSponsored")
    private boolean sponsored;
    private String sponsorName;

    // Attached Shoppable Item fields
    private String attachedType;
    private String attachedTitle;
    private String attachedSubtitle;
    private String attachedPrice;
}
