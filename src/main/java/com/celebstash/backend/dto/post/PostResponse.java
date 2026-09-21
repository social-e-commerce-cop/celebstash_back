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

    // User interactions.
    // The fields drop the "is" prefix so that Lombok's isLiked()/isSaved() getters resolve to
    // the same Jackson property as the field, and @JsonProperty then names it once on the wire.
    // Clients read "isLiked"/"isSaved"; naming the fields isLiked/isSaved instead would publish
    // both "liked" and "isLiked" for the same value.
    @JsonProperty("isLiked")
    private boolean liked;
    @JsonProperty("isShared")
    private boolean shared;
    @JsonProperty("isReposted")
    private boolean reposted;
    @JsonProperty("isSaved")
    private boolean saved;

    // No hand-written like accessors: Lombok generates isLiked() for the "liked" field above,
    // and the @JsonProperty there already publishes it as "isLiked". A second getter exposing
    // "liked" would emit the same value under two keys.

    // Comments (optional, may be loaded separately)
    private List<CommentResponse> comments;

    @JsonProperty("isSponsored")
    private boolean sponsored;
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
