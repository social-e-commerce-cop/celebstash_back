package com.celebstash.backend.dto.comment;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant createdAt;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Instant updatedAt;
    
    // Parent comment info (if this is a reply)
    private Long parentId;
<<<<<<< HEAD
    // See PostResponse: field drops the "is" prefix, @JsonProperty restores it on the wire.
    @com.fasterxml.jackson.annotation.JsonProperty("isReply")
    private boolean reply;
=======
    private Long parentUserId;
    private String parentUserName;

    @JsonProperty("isReply")
    private boolean isReply;

    @JsonProperty("isSelfReply")
    private boolean isSelfReply;
>>>>>>> d8b0c20a20f1fe235107c9e84bc1b64c7958d5ad
    
    // Counts
    private int likesCount;
    private int repliesCount;
    
    // User interactions
<<<<<<< HEAD
    @com.fasterxml.jackson.annotation.JsonProperty("isLiked")
    private boolean liked;
=======
    @JsonProperty("isLiked")
    private boolean isLiked;

    @JsonProperty("isLiked")
    public boolean isLiked() {
        return isLiked;
    }

    @JsonProperty("liked")
    public boolean getLiked() {
        return isLiked;
    }
>>>>>>> d8b0c20a20f1fe235107c9e84bc1b64c7958d5ad
    
    // Replies (optional, may be loaded separately)
    private List<CommentResponse> replies;
}