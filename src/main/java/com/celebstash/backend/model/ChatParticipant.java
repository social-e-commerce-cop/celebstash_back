package com.celebstash.backend.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_participants",
    uniqueConstraints = @UniqueConstraint(columnNames = {"conversation_id", "user_id"}))
public class ChatParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private ChatConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    private boolean isAdmin = false;

    @Builder.Default
    private boolean isMuted = false;

    @Builder.Default
    private boolean isArchived = false;

    @Builder.Default
    private boolean isPinned = false;

    @Builder.Default
    private boolean isFavorite = false;

    @Builder.Default
    private int unreadCount = 0;

    @CreationTimestamp
    private LocalDateTime joinedAt;
}
