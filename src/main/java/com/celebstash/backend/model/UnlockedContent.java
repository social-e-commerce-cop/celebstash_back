package com.celebstash.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "unlocked_contents", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"user_id", "content_id"})
})
public class UnlockedContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "content_id", nullable = false)
    private PremiumContent content;

    @Column(nullable = false)
    private LocalDateTime unlockedAt;

    private LocalDateTime expiresAt; // For time-boxed listen access models

    @Column(nullable = false)
    @Builder.Default
    private Integer playCap = 10;

    @Column(nullable = false)
    @Builder.Default
    private Integer remainingPlays = 10;

    @PrePersist
    protected void onCreate() {
        unlockedAt = LocalDateTime.now();
    }
}
