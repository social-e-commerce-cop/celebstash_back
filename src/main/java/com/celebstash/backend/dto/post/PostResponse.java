package com.celebstash.backend.dto.post;

import com.celebstash.backend.dto.comment.CommentResponse;
import com.celebstash.backend.dto.product.ProductResponse;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String userUsername;
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
    private long savesCount;

    // User interactions
    @JsonProperty("isLiked")
    private boolean isLiked;
    @JsonProperty("isShared")
    private boolean isShared;
    @JsonProperty("isReposted")
    private boolean isReposted;
    @JsonProperty("isSaved")
    private boolean isSaved;

    @JsonProperty("isLiked")
    public boolean isLiked() {
        return isLiked;
    }

    @JsonProperty("liked")
    public boolean getLiked() {
        return isLiked;
    }

    // Comments (optional, may be loaded separately)
    private List<CommentResponse> comments;

    private boolean isSponsored;
    private String sponsorName;

    // Attached Shoppable Item fields
    private String attachedType;
    private String attachedTitle;
    private String attachedSubtitle;
    private String attachedPrice;

    // Recent likers for the post
    private List<com.celebstash.backend.dto.user.UserPublicDTO> recentLikers;

    // Repost metadata (when post is fetched in a repost context)
    @JsonProperty("isRepost")
    private Boolean isRepost;
    private Long reposterId;
    private String reposterName;
    private String reposterUsername;
    private LocalDateTime repostedAt;
}
