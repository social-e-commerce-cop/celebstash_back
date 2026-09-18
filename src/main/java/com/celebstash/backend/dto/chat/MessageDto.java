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
    LocalDateTime sentAt
) {
    public record ReplyRefDto(
        Long messageId,
        String text,
        String senderName
    ) {}

    public record ReactionDto(
        String emoji,
        int count,
        boolean reactedByMe
    ) {}
}
