package com.celebstash.backend.model;

import com.celebstash.backend.model.enums.AccessPackageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "music_entitlements")
public class MusicEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "release_id", nullable = false)
    private MusicRelease release;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private MusicTrack track;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccessPackageType accessType;

    private Integer playsGranted;

    @Builder.Default
    private Integer playsUsed = 0;

    @Builder.Default
    private Integer playsRemaining = 0;

    @Builder.Default
    private boolean isPermanent = false;

    @Column(precision = 10, scale = 2)
    private BigDecimal amountPaid;

    private LocalDateTime firstPlayedAt;

    private LocalDateTime lastPlayedAt;

    @Column(nullable = false)
    private LocalDateTime purchasedAt;

    @PrePersist
    protected void onCreate() {
        purchasedAt = LocalDateTime.now();
        if (playsRemaining == null && playsGranted != null) {
            playsRemaining = playsGranted;
        }
    }
}
