package com.celebstash.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private int viewCount;
    private int reactionCount;
    private int replyCount;
    private int shareCount;
    private boolean allowReplies;
    private boolean allowReactions;
    private boolean isArchived;
    private boolean isViewedByMe;
    private String myReaction;
}
