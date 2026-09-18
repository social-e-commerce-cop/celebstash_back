package com.celebstash.backend.model;

import com.celebstash.backend.model.enums.MessageReadStatus;
import com.celebstash.backend.model.enums.MessageType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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
