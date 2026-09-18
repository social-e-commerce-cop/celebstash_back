package com.celebstash.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@ToString(exclude = {"parent", "replies", "likedBy", "post", "user"})
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "comments")
public class Comment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @Builder.Default
    @ManyToMany
    @JoinTable(
        name = "comment_likes",
        joinColumns = @JoinColumn(name = "comment_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> likedBy = new HashSet<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Builder.Default
    @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Comment> replies = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (likedBy == null) {
            likedBy = new HashSet<>();
        }
        if (replies == null) {
            replies = new HashSet<>();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public int getLikesCount() {
        return likedBy == null ? 0 : likedBy.size();
    }

    public int getRepliesCount() {
        return replies == null ? 0 : replies.size();
    }

    public boolean isLikedBy(User user) {
        if (likedBy == null || user == null || user.getId() == null) {
            return false;
        }
        return likedBy.stream().anyMatch(u -> u.getId() != null && u.getId().equals(user.getId()));
    }

    public void addLike(User user) {
        if (likedBy == null) {
            likedBy = new HashSet<>();
        }
        if (!isLikedBy(user)) {
            likedBy.add(user);
        }
    }

    public void removeLike(User user) {
        if (likedBy != null && user != null && user.getId() != null) {
            likedBy.removeIf(u -> u.getId() != null && u.getId().equals(user.getId()));
        }
    }

    public boolean isReply() {
        return parent != null;
    }
}