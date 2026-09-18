package com.celebstash.backend.dto.chat;

import com.celebstash.backend.model.enums.ConversationType;
import com.celebstash.backend.model.enums.MessageType;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationDto(
    Long id,
    ConversationType type,
    // Group fields
    String groupName,
    String groupAvatar,
    String groupDescription,
    Long createdById,
    // Participants
    List<ParticipantDto> participants,
    // Last message preview
    LastMessageDto lastMessage,
    // Per-user state
    int unreadCount,
    boolean isPinned,
    boolean isMuted,
    boolean isArchived,
    boolean isFavorite,
    LocalDateTime updatedAt
) {
    public record ParticipantDto(
        Long id,
        String fullName,
        String username,
        String profilePicture,
        boolean isAdmin
    ) {}

    public record LastMessageDto(
        String text,
        Long senderId,
        MessageType type,
        LocalDateTime sentAt
    ) {}
}
