package com.celebstash.backend.dto.chat;

import com.celebstash.backend.model.enums.MessageType;
import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(
    @NotNull MessageType type,
    String content,
    String mediaUrl,
    Integer voiceDuration,
    String documentName,
    String documentSize,
    Long replyToId,
    String systemText,
    /** Required when type == PRODUCT. Server validates the product exists before persisting. */
    Long productId,
    /** Required when type == POST. Server validates the post exists before persisting. */
    Long postId
) {}
