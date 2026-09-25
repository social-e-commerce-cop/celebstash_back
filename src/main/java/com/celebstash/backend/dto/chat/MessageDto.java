package com.celebstash.backend.dto.chat;

import com.celebstash.backend.model.enums.MessageReadStatus;
import com.celebstash.backend.model.enums.MessageType;

import java.time.LocalDateTime;
import java.util.List;

public record MessageDto(
    Long id,
    Long conversationId,
    Long senderId,
    String senderName,
    String senderAvatar,
    MessageType type,
    String content,
    String mediaUrl,
    Integer voiceDuration,
    String documentName,
    String documentSize,
    String systemText,
    ReplyRefDto replyTo,
    MessageReadStatus readStatus,
    boolean isPinned,
    boolean isStarred,
    boolean isEdited,
    boolean isDeleted,
    List<ReactionDto> reactions,
    LocalDateTime sentAt,
    /** Present when type == PRODUCT. Read live from the product system on every fetch. */
    SharedProductDto sharedProduct,
    /** Present when type == POST. Read live from the post system on every fetch. */
    SharedPostDto sharedPost
) {
    public record ReplyRefDto(
        Long messageId,
        String text,
        String senderName
    ) {}

    /** Minimal product projection for the in-chat card; the id drives "View Product". */
    public record SharedProductDto(
        Long id,
        String name,
        java.math.BigDecimal price,
        String imageUrl,
        String sellerName,
        String status
    ) {}

    /** Minimal post projection for the in-chat card; the id drives "View Post". */
    public record SharedPostDto(
        Long id,
        String description,
        String imageUrl,
        Long authorId,
        String authorName
    ) {}

    public record ReactionDto(
        String emoji,
        int count,
        boolean reactedByMe
    ) {}
}
