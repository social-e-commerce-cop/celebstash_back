package com.celebstash.backend.dto.comment;

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
public class CommentResponse {

    private Long id;
    private Long postId;
    private Long userId;
    private String userName;
    private String userUsername;
    private String userImageUrl;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Parent comment info (if this is a reply)
    private Long parentId;
    // See PostResponse: field drops the "is" prefix, @JsonProperty restores it on the wire.
    @com.fasterxml.jackson.annotation.JsonProperty("isReply")
    private boolean reply;
    
    // Counts
    private int likesCount;
    private int repliesCount;
    
    // User interactions
    @com.fasterxml.jackson.annotation.JsonProperty("isLiked")
    private boolean liked;
    
    // Replies (optional, may be loaded separately)
    private List<CommentResponse> replies;
}