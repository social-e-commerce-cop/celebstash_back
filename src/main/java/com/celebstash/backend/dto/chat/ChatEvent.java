package com.celebstash.backend.dto.chat;

/**
 * Envelope for real-time chat events published to {@code /topic/conversation/{id}/events}.
 *
 * <p>The legacy {@code /topic/conversation/{id}} destination continues to carry a bare
 * {@link MessageDto} for new messages so existing clients keep working; this richer stream carries
 * every event type and is what new clients should subscribe to.
 */
public record ChatEvent(
        String type,
        Long conversationId,
        Object payload
) {
    // Event type constants — keep in sync with the mobile client's ChatEventType.
    public static final String MESSAGE_NEW = "message.new";
    public static final String MESSAGE_UPDATED = "message.updated";
    public static final String MESSAGE_DELETED = "message.deleted";
    public static final String MESSAGE_REACTION = "message.reaction";
    public static final String MESSAGE_READ = "message.read";
    public static final String MESSAGE_DELIVERED = "message.delivered";
    public static final String TYPING_START = "typing.start";
    public static final String TYPING_STOP = "typing.stop";
    public static final String PRESENCE = "presence";
    public static final String CONVERSATION_UPDATED = "conversation.updated";
    public static final String GROUP_MEMBER_ADDED = "group.member.added";
    public static final String GROUP_MEMBER_REMOVED = "group.member.removed";

    public static ChatEvent of(String type, Long conversationId, Object payload) {
        return new ChatEvent(type, conversationId, payload);
    }

    /** Who is typing (or stopped) in a conversation. */
    public record TypingPayload(Long userId, String userName) {}

    /** Online/offline transition for a user, with last-seen when going offline. */
    public record PresencePayload(Long userId, boolean online, String lastSeen) {}

    /** Emitted when a participant has read up to this point in the conversation. */
    public record ReadPayload(Long userId, Long lastReadMessageId) {}

    /** Emitted when a message has reached a connected recipient, so the sender's ticks update. */
    public record DeliveredPayload(Long messageId, Long conversationId) {}

    /** Emitted when a message is removed so clients can collapse it without a refetch. */
    public record DeletedPayload(Long messageId, boolean forEveryone) {}

    /** Group membership change. {@code actorId} is who performed it (self, when leaving). */
    public record MemberPayload(Long userId, String userName, Long actorId) {}
}
