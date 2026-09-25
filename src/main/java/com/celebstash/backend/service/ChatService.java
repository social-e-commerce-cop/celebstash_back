package com.celebstash.backend.service;

import com.celebstash.backend.dto.chat.*;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.ConversationType;
import com.celebstash.backend.model.enums.MessageReadStatus;
import com.celebstash.backend.model.enums.MessageType;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationRepository conversationRepo;
    private final ChatMessageRepository messageRepo;
    private final ChatParticipantRepository participantRepo;
    private final UserRepository userRepository;
    private final UserService userService;
    private final ProductRepository productRepository;
    private final PostRepository postRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final PresenceService presenceService;
    private final NotificationService notificationService;

    // ── Real-time fan-out ────────────────────────────────────────────────────

    /**
     * Publishes a chat event to a conversation.
     *
     * <p>Two destinations on purpose: the bare {@code /topic/conversation/{id}} carries a plain
     * MessageDto for new messages, which is what the existing mobile client already subscribes to,
     * while {@code .../events} carries the typed envelope for everything else. That keeps older
     * clients working while newer ones get edits, deletes, reactions, typing and presence.
     *
     * <p>Broadcasting lives here rather than in the WebSocket controller so that REST writes are
     * delivered in real time too — previously only messages sent over the socket ever reached
     * other participants live.
     */
    private void publish(Long conversationId, String type, Object payload) {
        try {
            if (ChatEvent.MESSAGE_NEW.equals(type)) {
                messagingTemplate.convertAndSend("/topic/conversation/" + conversationId, payload);
            }
            messagingTemplate.convertAndSend(
                    "/topic/conversation/" + conversationId + "/events",
                    ChatEvent.of(type, conversationId, payload));
        } catch (Exception e) {
            // A broker problem must never roll back or fail the write that already succeeded.
            log.warn("Failed to publish {} for conversation {}: {}", type, conversationId, e.getMessage());
        }
    }

    /**
     * Transient typing signal — broadcast only, never stored.
     *
     * <p>Transactional because the membership check walks the conversation's lazy participants
     * collection; without a session that throws LazyInitializationException and the frame is
     * silently dropped.
     */
    @Transactional
    public void broadcastTyping(Long conversationId, User me, boolean typing) {
        ChatConversation conv = getConvForUser(conversationId, me);
        publish(conv.getId(),
                typing ? ChatEvent.TYPING_START : ChatEvent.TYPING_STOP,
                new ChatEvent.TypingPayload(me.getId(), me.getFullName()));
    }

    /** Announces a presence change to every conversation the user belongs to. */
    @Transactional
    public void broadcastPresence(User me, boolean online) {
        ChatEvent.PresencePayload payload =
                new ChatEvent.PresencePayload(me.getId(), online, presenceService.getLastSeen(me.getId()));
        for (ChatConversation conv : conversationRepo.findAllByParticipant(me)) {
            publish(conv.getId(), ChatEvent.PRESENCE, payload);
        }
    }

    // ── Get all conversations for current user ───────────────────────────────

    @Transactional
    public List<ConversationDto> getMyConversations() {
        User me = userService.getCurrentUser();
        List<ChatConversation> conversations = conversationRepo.findAllByParticipant(me);
        return conversations.stream()
                .map(c -> toConversationDto(c, me))
                .collect(Collectors.toList());
    }

    /**
     * One conversation with its current participant list. The info and group-settings screens need
     * membership that is fresh after an add/remove, and fetching the caller's entire conversation
     * list to pick one out of it would be wasteful. Membership is enforced by
     * {@link #getConvForUser}, so a non-member gets 403 rather than a leaked participant list.
     */
    @Transactional
    public ConversationDto getConversation(Long conversationId) {
        User me = userService.getCurrentUser();
        return toConversationDto(getConvForUser(conversationId, me), me);
    }

    // ── Start a direct DM ────────────────────────────────────────────────────

    @Transactional
    public ConversationDto startDirectConversation(CreateConversationRequest req) {
        User me = userService.getCurrentUser();
        User target = userRepository.findById(req.targetUserId())
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        // Return existing conversation if one already exists
        Optional<ChatConversation> existing = conversationRepo.findDirectConversation(me, target, ConversationType.DIRECT);
        if (existing.isPresent()) {
            return toConversationDto(existing.get(), me);
        }

        ChatConversation conv = ChatConversation.builder()
                .type(ConversationType.DIRECT)
                .createdBy(me)
                .build();

        conv = conversationRepo.save(conv);

        addParticipant(conv, me, true);
        addParticipant(conv, target, false);

        return toConversationDto(conv, me);
    }

    // ── Create a group ───────────────────────────────────────────────────────

    @Transactional
    public ConversationDto createGroup(CreateGroupRequest req) {
        User me = userService.getCurrentUser();

        ChatConversation built = ChatConversation.builder()
                .type(ConversationType.GROUP)
                .groupName(req.name())
                .groupDescription(req.description())
                .groupAvatar(req.avatarUrl())
                .createdBy(me)
                .build();

        // Use a final variable so it can be captured inside the lambda below
        final ChatConversation savedConv = conversationRepo.save(built);
        addParticipant(savedConv, me, true);

        for (Long memberId : req.memberIds()) {
            userRepository.findById(memberId).ifPresent(u -> addParticipant(savedConv, u, false));
        }

        // Send system message
        sendSystemMessage(savedConv, me.getFullName() + " created this group");

        return toConversationDto(savedConv, me);
    }

    // ── Get messages (paginated) ─────────────────────────────────────────────

    /**
     * Attachments shared in a conversation, newest first, for the shared-media gallery.
     *
     * <p>Kept separate from {@link #getMessages} because the gallery must not mark the thread as
     * read — opening "shared media" is not the same as reading the conversation.
     *
     * @param kind "media" (photos and videos), "documents", "links", or "products".
     */
    @Transactional
    public Page<MessageDto> getSharedMedia(Long conversationId, String kind, int page, int size) {
        User me = userService.getCurrentUser();
        ChatConversation conv = getConvForUser(conversationId, me);

        String k = kind == null ? "media" : kind.toLowerCase();
        List<MessageType> types = switch (k) {
            case "documents" -> List.of(MessageType.DOCUMENT);
            case "links" -> List.of(MessageType.LINK);
            case "products" -> List.of(MessageType.PRODUCT, MessageType.POST);
            default -> List.of(MessageType.IMAGE, MessageType.VIDEO);
        };
        // Product and post shares carry a reference, not an uploaded file.
        boolean requireMedia = !"products".equals(k) && !"links".equals(k);

        return messageRepo
                .findSharedForUser(conv, me.getId(), types, requireMedia, PageRequest.of(page, size))
                .map(m -> toMessageDto(m, me));
    }

    @Transactional
    public Page<MessageDto> getMessages(Long conversationId, int page, int size) {
        User me = userService.getCurrentUser();
        ChatConversation conv = getConvForUser(conversationId, me);

        // Mark messages as read
        messageRepo.markAllAsRead(conv, me.getId());

        // Reset unread count for this participant
        participantRepo.findByConversationAndUser(conv, me).ifPresent(p -> {
            p.setUnreadCount(0);
            participantRepo.save(p);
        });

        // Messages this user hid via "delete for me" are excluded in the query, so paging stays
        // correct — they remain visible to everyone else.
        Page<ChatMessage> msgs = messageRepo.findVisibleForUser(
                conv, me.getId(), PageRequest.of(page, size, Sort.by("sentAt").ascending()));

        // Tell the other participants how far this user has read, so their ticks update live.
        msgs.getContent().stream()
                .map(ChatMessage::getId)
                .max(Long::compareTo)
                .ifPresent(lastId -> publish(conv.getId(), ChatEvent.MESSAGE_READ,
                        new ChatEvent.ReadPayload(me.getId(), lastId)));

        return msgs.map(m -> toMessageDto(m, me));
    }

    // ── Send a message ───────────────────────────────────────────────────────

    @Transactional
    public MessageDto sendMessage(Long conversationId, SendMessageRequest req) {
        User me = userService.getCurrentUser();
        ChatConversation conv = getConvForUser(conversationId, me);

        ChatMessage.ChatMessageBuilder builder = ChatMessage.builder()
                .conversation(conv)
                .sender(me)
                .type(req.type())
                .content(req.content())
                .mediaUrl(req.mediaUrl())
                .voiceDuration(req.voiceDuration())
                .documentName(req.documentName())
                .documentSize(req.documentSize())
                .systemText(req.systemText());

        if (req.replyToId() != null) {
            // A reply must point at a message in this same conversation — otherwise a crafted
            // replyToId could pull a quoted preview out of a conversation the user cannot read.
            messageRepo.findById(req.replyToId())
                    .filter(m -> m.getConversation().getId().equals(conv.getId()))
                    .ifPresent(builder::replyTo);
        }

        // Referenced product/post are resolved server-side. The client sends only an id; if it
        // does not resolve, the message is rejected rather than stored as a card pointing nowhere.
        if (req.type() == MessageType.PRODUCT) {
            if (req.productId() == null) {
                throw new AppException("productId is required for a PRODUCT message", HttpStatus.BAD_REQUEST);
            }
            builder.sharedProduct(productRepository.findById(req.productId())
                    .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND)));
        }

        if (req.type() == MessageType.POST) {
            if (req.postId() == null) {
                throw new AppException("postId is required for a POST message", HttpStatus.BAD_REQUEST);
            }
            builder.sharedPost(postRepository.findById(req.postId())
                    .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND)));
        }

        ChatMessage msg = messageRepo.save(builder.build());

        // Bump updatedAt on conversation (@UpdateTimestamp fires automatically on save)
        conversationRepo.save(conv);

        // Increment unread for all other participants
        conv.getParticipants().stream()
                .filter(p -> !p.getUser().getId().equals(me.getId()))
                .forEach(p -> {
                    p.setUnreadCount(p.getUnreadCount() + 1);
                    participantRepo.save(p);
                });

        markDeliveredIfRecipientOnline(conv, me, msg);

        MessageDto dto = toMessageDto(msg, me);
        publish(conv.getId(), ChatEvent.MESSAGE_NEW, dto);
        notifyRecipients(conv, me, msg);
        return dto;
    }

    /**
     * Promotes SENT to DELIVERED once the message has actually reached a connected recipient.
     *
     * <p>Delivery is derived from presence rather than from a client saying "I got it", so a
     * client cannot mark someone else's message delivered. If nobody is connected the message
     * stays SENT and is promoted by {@link #getMessages} when the recipient next opens the
     * thread, which is also the point at which it becomes READ.
     *
     * <p>Known limitation: {@code readStatus} is one column on the message, not per-recipient, so
     * in a group this means "at least one other member was online", not "everyone received it".
     * Per-recipient receipts would need a join table; that is recorded rather than faked here.
     */
    private void markDeliveredIfRecipientOnline(ChatConversation conv, User sender, ChatMessage msg) {
        if (msg.getReadStatus() != MessageReadStatus.SENT) return;

        boolean anyRecipientOnline = conv.getParticipants().stream()
                .map(p -> p.getUser().getId())
                .filter(id -> !id.equals(sender.getId()))
                .anyMatch(presenceService::isOnline);

        if (!anyRecipientOnline) return;

        msg.setReadStatus(MessageReadStatus.DELIVERED);
        messageRepo.save(msg);
        publish(conv.getId(), ChatEvent.MESSAGE_DELIVERED,
                new ChatEvent.DeliveredPayload(msg.getId(), conv.getId()));
    }

    /**
     * Raises an in-app notification for participants who are not already reading this thread.
     *
     * <p>Skipped for anyone who muted the conversation or currently has it open — otherwise every
     * message would also buzz the person actively replying to it. Notification failures are
     * swallowed: a notification problem must not fail the send that already succeeded.
     *
     * <p>This uses the app's existing in-app Notification system. There is no device-push
     * infrastructure in this project (no stored push tokens, no FCM/APNs, expo-notifications not
     * installed), so no device push is attempted rather than pretending one was delivered.
     */
    private void notifyRecipients(ChatConversation conv, User sender, ChatMessage msg) {
        String preview = switch (msg.getType()) {
            case TEXT -> msg.getContent() != null && msg.getContent().length() > 80
                    ? msg.getContent().substring(0, 80) + "…"
                    : msg.getContent();
            case IMAGE -> "📷 Photo";
            case VIDEO -> "🎥 Video";
            case VOICE -> "🎤 Voice message";
            case DOCUMENT -> "📄 " + (msg.getDocumentName() != null ? msg.getDocumentName() : "File");
            case PRODUCT -> "🛍️ Shared a product";
            case POST -> "📣 Shared a post";
            default -> "New message";
        };

        String title = conv.getType() == ConversationType.GROUP && conv.getGroupName() != null
                ? conv.getGroupName()
                : sender.getFullName();

        for (ChatParticipant p : conv.getParticipants()) {
            User recipient = p.getUser();
            if (recipient.getId().equals(sender.getId())) continue;
            if (p.isMuted()) continue;
            if (presenceService.isViewing(recipient.getId(), conv.getId())) continue;

            try {
                notificationService.createNotification(
                        recipient,
                        title,
                        conv.getType() == ConversationType.GROUP
                                ? sender.getFullName() + ": " + preview
                                : preview,
                        NotificationType.NEW_MESSAGE,
                        conv.getId());   // relatedEntityId = conversation, so a tap can open it
            } catch (Exception e) {
                log.warn("Failed to notify {} about message {}: {}", recipient.getId(), msg.getId(), e.getMessage());
            }
        }
    }

    // ── Edit a message ───────────────────────────────────────────────────────

    @Transactional
    public MessageDto editMessage(Long messageId, String newContent) {
        User me = userService.getCurrentUser();
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new AppException("Message not found", HttpStatus.NOT_FOUND));

        if (!msg.getSender().getId().equals(me.getId())) {
            throw new AppException("You can only edit your own messages", HttpStatus.FORBIDDEN);
        }

        msg.setContent(newContent);
        msg.setEdited(true);
        MessageDto dto = toMessageDto(messageRepo.save(msg), me);
        publish(msg.getConversation().getId(), ChatEvent.MESSAGE_UPDATED, dto);
        return dto;
    }

    // ── Delete a message ─────────────────────────────────────────────────────

    @Transactional
    public void deleteMessage(Long messageId, boolean forEveryone) {
        User me = userService.getCurrentUser();
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new AppException("Message not found", HttpStatus.NOT_FOUND));

        // Membership is required for either kind of delete. Without it any authenticated user
        // could destroy a message in a conversation they have nothing to do with.
        getConvForUser(msg.getConversation().getId(), me);

        if (forEveryone) {
            if (!msg.getSender().getId().equals(me.getId())) {
                throw new AppException("You can only delete your own messages for everyone", HttpStatus.FORBIDDEN);
            }
            // Soft delete, and drop the attachment references so a deleted message cannot keep
            // handing out media or a product/post card.
            msg.setDeleted(true);
            msg.setContent(null);
            msg.setMediaUrl(null);
            msg.setSharedProduct(null);
            msg.setSharedPost(null);
            messageRepo.save(msg);
            publish(msg.getConversation().getId(), ChatEvent.MESSAGE_DELETED,
                    new ChatEvent.DeletedPayload(msg.getId(), true));
        } else {
            // "Delete for me" hides the row for this user only. It used to call
            // messageRepo.delete(), which erased the message for every participant.
            msg.getHiddenFor().add(me.getId());
            messageRepo.save(msg);
        }
    }

    // ── Toggle participant settings ───────────────────────────────────────────

    @Transactional
    public void toggleMute(Long conversationId) {
        toggleParticipantFlag(conversationId, "mute");
    }

    @Transactional
    public void toggleArchive(Long conversationId) {
        toggleParticipantFlag(conversationId, "archive");
    }

    @Transactional
    public void togglePin(Long conversationId) {
        toggleParticipantFlag(conversationId, "pin");
    }

    @Transactional
    public void toggleFavorite(Long conversationId) {
        toggleParticipantFlag(conversationId, "favorite");
    }

    // ── Toggle message pin ───────────────────────────────────────────────────

    @Transactional
    public MessageDto toggleMessagePin(Long messageId) {
        User me = userService.getCurrentUser();
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new AppException("Message not found", HttpStatus.NOT_FOUND));
        // Without this, any authenticated user could pin a message in a conversation they are
        // not part of just by guessing its id.
        getConvForUser(msg.getConversation().getId(), me);
        msg.setPinned(!msg.isPinned());
        MessageDto dto = toMessageDto(messageRepo.save(msg), me);
        publish(msg.getConversation().getId(), ChatEvent.MESSAGE_UPDATED, dto);
        return dto;
    }

    // ── Toggle message star ──────────────────────────────────────────────────

    @Transactional
    public MessageDto toggleMessageStar(Long messageId) {
        User me = userService.getCurrentUser();
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new AppException("Message not found", HttpStatus.NOT_FOUND));
        getConvForUser(msg.getConversation().getId(), me);
        msg.setStarred(!msg.isStarred());
        // Not published: starring is a personal bookmark, not a conversation-wide change.
        return toMessageDto(messageRepo.save(msg), me);
    }

    // ── React to a message ───────────────────────────────────────────────────

    @Transactional
    public MessageDto reactToMessage(Long messageId, String emoji) {
        User me = userService.getCurrentUser();
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new AppException("Message not found", HttpStatus.NOT_FOUND));

        // Only participants may react — otherwise any id could be reacted to from outside.
        getConvForUser(msg.getConversation().getId(), me);

        // Toggle: remove if already reacted with this emoji, else add
        Optional<ChatMessageReaction> existing = msg.getReactions().stream()
                .filter(r -> r.getUser().getId().equals(me.getId()) && r.getEmoji().equals(emoji))
                .findFirst();

        if (existing.isPresent()) {
            msg.getReactions().remove(existing.get());
        } else {
            ChatMessageReaction reaction = ChatMessageReaction.builder()
                    .message(msg)
                    .user(me)
                    .emoji(emoji)
                    .build();
            msg.getReactions().add(reaction);
        }

        MessageDto dto = toMessageDto(messageRepo.save(msg), me);
        publish(msg.getConversation().getId(), ChatEvent.MESSAGE_REACTION, dto);
        return dto;
    }

    // ── Update group info ────────────────────────────────────────────────────

    @Transactional
    public ConversationDto updateGroup(Long conversationId, UpdateGroupRequest req) {
        User me = userService.getCurrentUser();
        ChatConversation conv = getConvForUser(conversationId, me);

        if (conv.getType() != ConversationType.GROUP) {
            throw new AppException("Not a group conversation", HttpStatus.BAD_REQUEST);
        }

        if (req.name() != null) conv.setGroupName(req.name());
        if (req.description() != null) conv.setGroupDescription(req.description());
        if (req.avatarUrl() != null) conv.setGroupAvatar(req.avatarUrl());

        return toConversationDto(conversationRepo.save(conv), me);
    }

    // ── Search users ─────────────────────────────────────────────────────────

    public List<UserSearchDto> searchUsers(String query) {
        return userRepository.searchByNameOrUsername(query).stream()
                .map(u -> new UserSearchDto(u.getId(), u.getFullName(), u.getUsername(), u.getProfilePicture(), u.getBio()))
                .collect(Collectors.toList());
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    // ── Group management ─────────────────────────────────────────────────────
    // Every check below is server-side. Roles supplied by the client are ignored entirely:
    // membership and admin status are always re-read from the database.

    /** Loads a GROUP conversation the caller belongs to, rejecting DMs and non-members. */
    private ChatConversation getGroupForUser(Long conversationId, User me) {
        ChatConversation conv = getConvForUser(conversationId, me);
        if (conv.getType() != ConversationType.GROUP) {
            throw new AppException("Not a group conversation", HttpStatus.BAD_REQUEST);
        }
        return conv;
    }

    /** Loads a group and asserts the caller is one of its admins. */
    private ChatConversation getGroupAsAdmin(Long conversationId, User me) {
        ChatConversation conv = getGroupForUser(conversationId, me);
        boolean isAdmin = conv.getParticipants().stream()
                .anyMatch(p -> p.getUser().getId().equals(me.getId()) && p.isAdmin());
        if (!isAdmin) {
            throw new AppException("Only group admins can perform this action", HttpStatus.FORBIDDEN);
        }
        return conv;
    }

    @Transactional
    public ConversationDto addMembers(Long conversationId, List<Long> userIds) {
        User me = userService.getCurrentUser();
        ChatConversation conv = getGroupAsAdmin(conversationId, me);

        for (Long id : userIds) {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new AppException("User not found: " + id, HttpStatus.NOT_FOUND));
            if (participantRepo.existsByConversationAndUser(conv, user)) {
                continue; // already a member — adding twice would duplicate the row
            }
            addParticipant(conv, user, false);
            sendSystemMessage(conv, user.getFullName() + " was added by " + me.getFullName());
            publish(conv.getId(), ChatEvent.GROUP_MEMBER_ADDED,
                    new ChatEvent.MemberPayload(user.getId(), user.getFullName(), me.getId()));
        }

        ChatConversation refreshed = conversationRepo.findById(conv.getId()).orElseThrow();
        ConversationDto dto = toConversationDto(refreshed, me);
        publish(conv.getId(), ChatEvent.CONVERSATION_UPDATED, dto);
        return dto;
    }

    @Transactional
    public void removeMember(Long conversationId, Long userId) {
        User me = userService.getCurrentUser();
        ChatConversation conv = getGroupAsAdmin(conversationId, me);

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        ChatParticipant participant = participantRepo.findByConversationAndUser(conv, target)
                .orElseThrow(() -> new AppException("User is not a member of this group", HttpStatus.NOT_FOUND));

        // The creator anchors the group (system messages are attributed to them) and removing
        // them would orphan it, so they can only leave voluntarily.
        if (conv.getCreatedBy() != null && conv.getCreatedBy().getId().equals(userId)) {
            throw new AppException("The group creator cannot be removed", HttpStatus.FORBIDDEN);
        }

        // ChatConversation.participants is mapped cascade=ALL/orphanRemoval=true, so the parent
        // owns the child's lifecycle. Calling participantRepo.delete() alone is silently undone:
        // the parent still holds the reference and Hibernate re-persists it on flush. Detaching
        // from the collection is what actually removes the row.
        detachParticipant(conv, participant);
        sendSystemMessage(conv, target.getFullName() + " was removed by " + me.getFullName());
        publish(conv.getId(), ChatEvent.GROUP_MEMBER_REMOVED,
                new ChatEvent.MemberPayload(target.getId(), target.getFullName(), me.getId()));
    }

    /** Leaving is self-service: no admin rights needed, and admins may leave too. */
    @Transactional
    public void leaveGroup(Long conversationId) {
        User me = userService.getCurrentUser();
        ChatConversation conv = getGroupForUser(conversationId, me);

        ChatParticipant mine = participantRepo.findByConversationAndUser(conv, me)
                .orElseThrow(() -> new AppException("You are not a member of this group", HttpStatus.NOT_FOUND));

        // Don't strand the group without an admin: promote the longest-standing remaining member.
        boolean wasOnlyAdmin = mine.isAdmin() && conv.getParticipants().stream()
                .noneMatch(p -> p.isAdmin() && !p.getUser().getId().equals(me.getId()));

        detachParticipant(conv, mine);
        sendSystemMessage(conv, me.getFullName() + " left the group");
        publish(conv.getId(), ChatEvent.GROUP_MEMBER_REMOVED,
                new ChatEvent.MemberPayload(me.getId(), me.getFullName(), me.getId()));

        if (wasOnlyAdmin) {
            conv.getParticipants().stream()
                    .filter(p -> !p.getUser().getId().equals(me.getId()))
                    .min(Comparator.comparing(ChatParticipant::getJoinedAt,
                            Comparator.nullsLast(Comparator.naturalOrder())))
                    .ifPresent(next -> {
                        next.setAdmin(true);
                        participantRepo.save(next);
                        sendSystemMessage(conv, next.getUser().getFullName() + " is now an admin");
                    });
        }
    }

    /** Grant or revoke admin. Only an existing admin may change roles. */
    @Transactional
    public ConversationDto setMemberAdmin(Long conversationId, Long userId, boolean admin) {
        User me = userService.getCurrentUser();
        ChatConversation conv = getGroupAsAdmin(conversationId, me);

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        ChatParticipant participant = participantRepo.findByConversationAndUser(conv, target)
                .orElseThrow(() -> new AppException("User is not a member of this group", HttpStatus.NOT_FOUND));

        // Refuse to remove the last admin — the group would become unmanageable.
        if (!admin) {
            long remainingAdmins = conv.getParticipants().stream()
                    .filter(p -> p.isAdmin() && !p.getUser().getId().equals(userId))
                    .count();
            if (remainingAdmins == 0) {
                throw new AppException("A group must keep at least one admin", HttpStatus.BAD_REQUEST);
            }
        }

        participant.setAdmin(admin);
        participantRepo.save(participant);
        sendSystemMessage(conv, target.getFullName() + (admin ? " is now an admin" : " is no longer an admin"));

        ConversationDto dto = toConversationDto(conversationRepo.findById(conv.getId()).orElseThrow(), me);
        publish(conv.getId(), ChatEvent.CONVERSATION_UPDATED, dto);
        return dto;
    }

    /**
     * Removes a participant through the owning collection so orphanRemoval actually deletes it,
     * then flushes. The repository delete on its own is reverted by the parent's cascade.
     */
    private void detachParticipant(ChatConversation conv, ChatParticipant participant) {
        conv.getParticipants().removeIf(p -> p.getId().equals(participant.getId()));
        participantRepo.delete(participant);
        conversationRepo.saveAndFlush(conv);
    }

    private void addParticipant(ChatConversation conv, User user, boolean isAdmin) {
        ChatParticipant p = ChatParticipant.builder()
                .conversation(conv)
                .user(user)
                .isAdmin(isAdmin)
                .build();
        participantRepo.save(p);
    }

    private void sendSystemMessage(ChatConversation conv, String text) {
        ChatMessage msg = ChatMessage.builder()
                .conversation(conv)
                .sender(conv.getCreatedBy())
                .type(MessageType.SYSTEM)
                .systemText(text)
                .build();
        messageRepo.save(msg);
    }

    private ChatConversation getConvForUser(Long conversationId, User user) {
        ChatConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new AppException("Conversation not found", HttpStatus.NOT_FOUND));
        if (conv.getParticipants().stream().noneMatch(p -> p.getUser().getId().equals(user.getId()))) {
            throw new AppException("You are not a member of this conversation", HttpStatus.FORBIDDEN);
        }
        return conv;
    }

    private void toggleParticipantFlag(Long conversationId, String flag) {
        User me = userService.getCurrentUser();
        ChatConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new AppException("Conversation not found", HttpStatus.NOT_FOUND));
        participantRepo.findByConversationAndUser(conv, me).ifPresent(p -> {
            switch (flag) {
                case "mute" -> p.setMuted(!p.isMuted());
                case "archive" -> p.setArchived(!p.isArchived());
                case "pin" -> p.setPinned(!p.isPinned());
                case "favorite" -> p.setFavorite(!p.isFavorite());
            }
            participantRepo.save(p);
        });
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private ConversationDto toConversationDto(ChatConversation conv, User me) {
        ChatParticipant myParticipant = conv.getParticipants().stream()
                .filter(p -> p.getUser().getId().equals(me.getId()))
                .findFirst()
                .orElse(null);

        List<ChatMessage> latestMessages = messageRepo.findTop1ByConversationOrderBySentAtDesc(conv);
        ChatMessage lastMsg = latestMessages.isEmpty() ? null : latestMessages.get(0);

        List<ConversationDto.ParticipantDto> participantDtos = conv.getParticipants().stream()
                .map(p -> new ConversationDto.ParticipantDto(
                        p.getUser().getId(),
                        p.getUser().getFullName(),
                        p.getUser().getUsername(),
                        p.getUser().getProfilePicture(),
                        p.isAdmin()
                ))
                .collect(Collectors.toList());

        ConversationDto.LastMessageDto lastMsgDto = lastMsg == null ? null : new ConversationDto.LastMessageDto(
                lastMsg.getContent() != null ? lastMsg.getContent() : lastMsg.getSystemText(),
                lastMsg.getSender().getId(),
                lastMsg.getType(),
                lastMsg.getSentAt()
        );

        return new ConversationDto(
                conv.getId(),
                conv.getType(),
                conv.getGroupName(),
                conv.getGroupAvatar(),
                conv.getGroupDescription(),
                conv.getCreatedBy() != null ? conv.getCreatedBy().getId() : null,
                participantDtos,
                lastMsgDto,
                myParticipant != null ? myParticipant.getUnreadCount() : 0,
                myParticipant != null && myParticipant.isPinned(),
                myParticipant != null && myParticipant.isMuted(),
                myParticipant != null && myParticipant.isArchived(),
                myParticipant != null && myParticipant.isFavorite(),
                conv.getUpdatedAt()
        );
    }

    public MessageDto toMessageDto(ChatMessage msg, User me) {
        // Build reaction summary
        Map<String, long[]> reactionMap = new LinkedHashMap<>();
        for (ChatMessageReaction r : msg.getReactions()) {
            reactionMap.computeIfAbsent(r.getEmoji(), k -> new long[]{0, 0});
            reactionMap.get(r.getEmoji())[0]++;
            if (r.getUser().getId().equals(me.getId())) {
                reactionMap.get(r.getEmoji())[1] = 1;
            }
        }

        List<MessageDto.ReactionDto> reactionDtos = reactionMap.entrySet().stream()
                .map(e -> new MessageDto.ReactionDto(e.getKey(), (int) e.getValue()[0], e.getValue()[1] == 1))
                .collect(Collectors.toList());

        MessageDto.ReplyRefDto replyRef = null;
        if (msg.getReplyTo() != null) {
            ChatMessage reply = msg.getReplyTo();
            replyRef = new MessageDto.ReplyRefDto(
                    reply.getId(),
                    reply.getContent() != null ? reply.getContent() : "(media)",
                    reply.getSender().getFullName()
            );
        }

        // Read the current state of a shared product/post rather than a snapshot taken when the
        // message was sent, so price, imagery and availability stay correct in old messages.
        // A deleted message must not keep leaking its attachment through these refs.
        MessageDto.SharedProductDto productRef = null;
        if (!msg.isDeleted() && msg.getSharedProduct() != null) {
            Product p = msg.getSharedProduct();
            productRef = new MessageDto.SharedProductDto(
                    p.getId(),
                    p.getName(),
                    p.getPrice(),
                    (p.getImageUrls() != null && !p.getImageUrls().isEmpty()) ? p.getImageUrls().get(0) : null,
                    p.getSeller() != null
                            ? (p.getSeller().getUsername() != null ? p.getSeller().getUsername() : p.getSeller().getFullName())
                            : null,
                    p.getStatus() != null ? p.getStatus().name() : null
            );
        }

        MessageDto.SharedPostDto postRef = null;
        if (!msg.isDeleted() && msg.getSharedPost() != null) {
            Post po = msg.getSharedPost();
            postRef = new MessageDto.SharedPostDto(
                    po.getId(),
                    po.getDescription(),
                    (po.getImageUrls() != null && !po.getImageUrls().isEmpty()) ? po.getImageUrls().get(0) : null,
                    po.getUser() != null ? po.getUser().getId() : null,
                    po.getUser() != null ? po.getUser().getFullName() : null
            );
        }

        return new MessageDto(
                msg.getId(),
                msg.getConversation().getId(),
                msg.getSender().getId(),
                msg.getSender().getFullName(),
                msg.getSender().getProfilePicture(),
                msg.getType(),
                msg.getContent(),
                msg.getMediaUrl(),
                msg.getVoiceDuration(),
                msg.getDocumentName(),
                msg.getDocumentSize(),
                msg.getSystemText(),
                replyRef,
                msg.getReadStatus(),
                msg.isPinned(),
                msg.isStarred(),
                msg.isEdited(),
                msg.isDeleted(),
                reactionDtos,
                msg.getSentAt(),
                productRef,
                postRef
        );
    }
}
