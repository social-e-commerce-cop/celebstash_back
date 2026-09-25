package com.celebstash.backend.model;

import com.celebstash.backend.model.enums.MessageReadStatus;
import com.celebstash.backend.model.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MessageType type = MessageType.TEXT;

    // Text content
    @Column(columnDefinition = "TEXT")
    private String content;

    // Media URLs (stored in file storage)
    private String mediaUrl;

    // For voice messages
    private Integer voiceDuration; // seconds

    // For document messages
    private String documentName;
    private String documentSize;

    // Reply reference
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_id")
    private ChatMessage replyTo;

    /**
     * Shared product (MessageType.PRODUCT). Stored as a reference rather than a copy so the card
     * always renders the product's current name, price, image and status from the product system
     * instead of a snapshot that silently goes stale.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_product_id")
    private Product sharedProduct;

    /** Shared post (MessageType.POST). Referenced, not duplicated, for the same reason. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shared_post_id")
    private Post sharedPost;

    /**
     * Users who deleted this message "for me only". The row stays so other participants keep
     * seeing it; it is simply filtered out of the hider's view. Previously "delete for me"
     * issued a hard delete, which removed the message for everyone in the conversation.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "chat_message_hidden_for",
            joinColumns = @JoinColumn(name = "message_id"))
    @Column(name = "user_id")
    @Builder.Default
    private Set<Long> hiddenFor = new HashSet<>();

    // System text (e.g., "You created this group")
    private String systemText;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MessageReadStatus readStatus = MessageReadStatus.SENT;

    @Builder.Default
    private boolean isPinned = false;

    @Builder.Default
    private boolean isStarred = false;

    @Builder.Default
    private boolean isEdited = false;

    @Builder.Default
    private boolean isDeleted = false;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ChatMessageReaction> reactions = new ArrayList<>();

    @CreationTimestamp
    private LocalDateTime sentAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
