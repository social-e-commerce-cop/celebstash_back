package com.celebstash.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stories")
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String mediaUrl;

    private String thumbnailUrl;

    @Column(nullable = false, columnDefinition = "varchar(255) default 'IMAGE'")
    @Builder.Default
    private String mediaType = "IMAGE"; // IMAGE or VIDEO

    @Column(length = 500)
    private String caption;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private Product product;

    @Column(nullable = false, columnDefinition = "varchar(255) default 'PUBLIC'")
    @Builder.Default
    private String visibility = "PUBLIC"; // PUBLIC, FOLLOWERS, CLOSE_FRIENDS

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "view_count", nullable = false)
    @Builder.Default
    private int viewCount = 0;

    @Column(name = "reaction_count", nullable = false)
    @Builder.Default
    private int reactionCount = 0;

    @Column(name = "reply_count", nullable = false)
    @Builder.Default
    private int replyCount = 0;

    @Column(name = "share_count", nullable = false)
    @Builder.Default
    private int shareCount = 0;

    @Column(name = "allow_replies", nullable = false)
    @Builder.Default
    private boolean allowReplies = true;

    @Column(name = "allow_reactions", nullable = false)
    @Builder.Default
    private boolean allowReactions = true;

    @Column(name = "is_archived", nullable = false)
    @Builder.Default
    private boolean isArchived = false;

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StoryView> views = new ArrayList<>();

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StoryReaction> reactions = new ArrayList<>();

    @OneToMany(mappedBy = "story", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<StoryReply> replies = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (expiresAt == null) {
            expiresAt = createdAt.plusHours(24);
        }
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}