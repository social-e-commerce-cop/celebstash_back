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
    String systemText
) {}
