package com.celebstash.backend.model;

import com.celebstash.backend.model.enums.MusicAccessStatus;
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
@Table(
    name = "music_access",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_music_access_user_release", columnNames = {"user_id", "release_id"})
    }
)
public class MusicAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonIgnoreProperties({"password", "roles", "products", "applications", "posts", "handler", "hibernateLazyInitializer"})
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "release_id", nullable = false)
    private MusicRelease release;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MusicAccessStatus status = MusicAccessStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDateTime grantedAt;

    @Builder.Default
    private boolean isGift = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gifted_by_id")
    @JsonIgnoreProperties({"password", "roles", "products", "applications", "posts", "handler", "hibernateLazyInitializer"})
    private User giftedBy;

    @Column(length = 1000)
    private String giftMessage;

    @Column(length = 4000)
    private String grantedBenefitsSnapshot;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
        if (status == null) {
            status = MusicAccessStatus.ACTIVE;
        }
    }
}
