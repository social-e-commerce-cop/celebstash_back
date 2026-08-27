package com.celebstash.backend.service;

import com.celebstash.backend.dto.chat.*;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.ConversationType;
import com.celebstash.backend.model.enums.MessageType;
import com.celebstash.backend.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final ChatConversationRepository conversationRepo;
    private final ChatMessageRepository messageRepo;
    private final ChatParticipantRepository participantRepo;
    private final UserRepository userRepository;
    private final UserService userService;

    // ── Get all conversations for current user ───────────────────────────────

    @Transactional
    public List<ConversationDto> getMyConversations() {
        User me = userService.getCurrentUser();
        List<ChatConversation> conversations = conversationRepo.findAllByParticipant(me);
        return conversations.stream()
                .map(c -> toConversationDto(c, me))
                .collect(Collectors.toList());
    }

    // ── Start a direct DM ────────────────────────────────────────────────────

    @Transactional
    public ConversationDto startDirectConversation(CreateConversationRequest req) {
        User me = userService.getCurrentUser();
        User target = userRepository.findById(req.targetUserId())
                .orElseThrow(() -> new RuntimeException("User not found"));

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

        Page<ChatMessage> msgs = messageRepo.findByConversationOrderBySentAtAsc(
                conv, PageRequest.of(page, size, Sort.by("sentAt").ascending()));

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
            messageRepo.findById(req.replyToId()).ifPresent(builder::replyTo);
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

        return toMessageDto(msg, me);
    }

    // ── Edit a message ───────────────────────────────────────────────────────

    @Transactional
    public MessageDto editMessage(Long messageId, String newContent) {
        User me = userService.getCurrentUser();
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (!msg.getSender().getId().equals(me.getId())) {
            throw new RuntimeException("Cannot edit another user's message");
        }

        msg.setContent(newContent);
        msg.setEdited(true);
        return toMessageDto(messageRepo.save(msg), me);
    }

    // ── Delete a message ─────────────────────────────────────────────────────

    @Transactional
    public void deleteMessage(Long messageId, boolean forEveryone) {
        User me = userService.getCurrentUser();
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        if (forEveryone) {
            if (!msg.getSender().getId().equals(me.getId())) {
                throw new RuntimeException("Cannot delete another user's message for everyone");
            }
            msg.setDeleted(true);
            msg.setContent(null);
            msg.setMediaUrl(null);
            messageRepo.save(msg);
        } else {
            messageRepo.delete(msg);
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
                .orElseThrow(() -> new RuntimeException("Message not found"));
        msg.setPinned(!msg.isPinned());
        return toMessageDto(messageRepo.save(msg), me);
    }

    // ── Toggle message star ──────────────────────────────────────────────────

    @Transactional
    public MessageDto toggleMessageStar(Long messageId) {
        User me = userService.getCurrentUser();
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        msg.setStarred(!msg.isStarred());
        return toMessageDto(messageRepo.save(msg), me);
    }

    // ── React to a message ───────────────────────────────────────────────────

    @Transactional
    public MessageDto reactToMessage(Long messageId, String emoji) {
        User me = userService.getCurrentUser();
        ChatMessage msg = messageRepo.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

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

        return toMessageDto(messageRepo.save(msg), me);
    }

    // ── Update group info ────────────────────────────────────────────────────

    @Transactional
    public ConversationDto updateGroup(Long conversationId, UpdateGroupRequest req) {
        User me = userService.getCurrentUser();
        ChatConversation conv = getConvForUser(conversationId, me);

        if (conv.getType() != ConversationType.GROUP) {
            throw new RuntimeException("Not a group conversation");
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
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        if (conv.getParticipants().stream().noneMatch(p -> p.getUser().getId().equals(user.getId()))) {
            throw new RuntimeException("Access denied");
        }
        return conv;
    }

    private void toggleParticipantFlag(Long conversationId, String flag) {
        User me = userService.getCurrentUser();
        ChatConversation conv = conversationRepo.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
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
                msg.getSentAt()
        );
    }
}
