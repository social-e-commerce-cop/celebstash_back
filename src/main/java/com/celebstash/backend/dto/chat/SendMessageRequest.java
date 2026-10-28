package com.celebstash.backend.dto.chat;

import com.celebstash.backend.model.enums.MessageType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

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
    Long postId,
    /**
     * Optional client-generated id (max 64 chars). Sending the same id twice — a retry after a
     * timeout, a double tap — returns the message already stored instead of creating another.
     */
    @Size(max = 64) String clientMessageId
) {}
