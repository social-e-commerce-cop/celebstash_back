package com.celebstash.backend.model;

import com.celebstash.backend.model.enums.FollowerType;
import com.celebstash.backend.model.enums.Role;
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
@Table(name = "followers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"follower_id", "following_id"})
})
public class Follower {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_id", nullable = false)
    private User follower;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_id", nullable = false)
    private User following;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FollowerType type;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        // Determine follower type based on the role of the user being followed
        if (following.getRole() == null || following.getRole() == Role.USER) {
            type = FollowerType.MATE;
        } else if (following.getRole() == Role.ARTIST) {
            type = FollowerType.TRIBE;
        } else {
            type = FollowerType.MATE; // Default
        }
    }
}