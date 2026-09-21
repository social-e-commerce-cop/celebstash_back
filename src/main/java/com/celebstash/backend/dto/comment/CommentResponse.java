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
    private Long parentUserId;
    private String parentUserName;

    // These fields drop the "is" prefix so that Lombok's isReply()/isLiked() getters resolve to
    // the same Jackson property as the field; @JsonProperty then names it once on the wire.
    // Naming a field "isReply" instead publishes BOTH "reply" and "isReply" for one value.
    // Clients read the "is" form.
    @JsonProperty("isReply")
    private boolean reply;

    @JsonProperty("isSelfReply")
    private boolean selfReply;

    // Counts
    private int likesCount;
    private int repliesCount;

    // User interactions
    @JsonProperty("isLiked")
    private boolean liked;

    // Replies (optional, may be loaded separately)
    private List<CommentResponse> replies;
}