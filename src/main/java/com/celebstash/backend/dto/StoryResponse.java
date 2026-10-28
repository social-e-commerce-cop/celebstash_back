package com.celebstash.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryResponse {
    private Long id;
    private Long userId;
    private String username;
    private String userFullName;
    private String userAvatar;
    private String userRole;
    private String mediaUrl;
    private String thumbnailUrl;
    private String mediaType;
    private String caption;
    private String visibility;
    private Long productId;
    /** UTC instants (serialised with a trailing "Z"); expiry is decided by the server. */
    private Instant createdAt;
    private Instant expiresAt;
    private int viewCount;
    private int reactionCount;
    private int replyCount;
    private int shareCount;
    private boolean allowReplies;
    private boolean allowReactions;

    // Same convention as PostResponse: the fields drop the "is" prefix so Lombok's isX() getter
    // and the field resolve to one JSON property, and @JsonProperty names it once. A field named
    // isViewedByMe was published as "viewedByMe", so clients reading isViewedByMe always saw
    // undefined and every story looked unseen.
    @JsonProperty("isArchived")
    private boolean archived;
    @JsonProperty("isViewedByMe")
    private boolean viewedByMe;

    private String myReaction;
}
