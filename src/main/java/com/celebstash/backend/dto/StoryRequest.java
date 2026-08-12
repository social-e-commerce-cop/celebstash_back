package com.celebstash.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryRequest {
    private String mediaUrl;
    private String thumbnailUrl;
    private String mediaType; // IMAGE or VIDEO
    private String caption;
    private String visibility; // PUBLIC, FOLLOWERS, CLOSE_FRIENDS
    private Long productId;
    @Builder.Default
    private boolean allowReplies = true;
    @Builder.Default
    private boolean allowReactions = true;
}
