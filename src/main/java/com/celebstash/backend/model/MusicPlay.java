package com.celebstash.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "music_plays", indexes = {
    @Index(name = "idx_music_plays_release", columnList = "release_id"),
    @Index(name = "idx_music_plays_user", columnList = "user_id"),
    @Index(name = "idx_music_plays_played_at", columnList = "played_at")
})
public class MusicPlay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "release_id", nullable = false)
    private MusicRelease release;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private MusicTrack track;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"password", "roles", "products", "applications", "posts", "handler", "hibernateLazyInitializer"})
    private User user;

    @Column(name = "played_at", nullable = false)
    private LocalDateTime playedAt;

    private Integer durationSeconds;

    @PrePersist
    protected void onCreate() {
        if (playedAt == null) {
            playedAt = LocalDateTime.now();
        }
    }
}
