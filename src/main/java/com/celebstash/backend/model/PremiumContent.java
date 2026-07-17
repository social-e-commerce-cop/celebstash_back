package com.celebstash.backend.model;

import com.celebstash.backend.model.enums.PremiumAccessModel;
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
@Table(name = "premium_contents")
public class PremiumContent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private String fileName;

    private String coverImageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PremiumAccessModel accessModel;

    @Column(nullable = false, precision = 38, scale = 2)
    private BigDecimal price;

    private LocalDateTime earlyAccessUntil;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
