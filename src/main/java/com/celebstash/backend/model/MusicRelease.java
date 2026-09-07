package com.celebstash.backend.model;

import com.celebstash.backend.model.enums.AccessPackageType;
import com.celebstash.backend.model.enums.ReleaseType;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "music_releases")
public class MusicRelease {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReleaseType releaseType;

    private String coverArtUrl;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "artist_id", nullable = false)
    @JsonIgnoreProperties({"password", "roles", "products", "applications", "posts", "handler", "hibernateLazyInitializer"})
    private User artist;

    private String featuredArtists;

    private String genre;

    private String subgenre;

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String releaseStory;

    private LocalDateTime releaseDate;

    private LocalDateTime publicReleaseDate;

    private String language;

    private String countryOfOrigin;

    @Builder.Default
    private boolean isExplicit = false;

    @Column(length = 1000)
    private String copyrightInfo;

    @Column(length = 2000)
    private String credits;

    @Column(nullable = false)
    @Builder.Default
    private String status = "PUBLISHED";

    @Column(nullable = true)
    @Builder.Default
    private String availabilityStatus = "UNRELEASED";

    @Column(precision = 10, scale = 2)
    private BigDecimal albumPrice;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private AccessPackageType defaultAccessPackageType = AccessPackageType.LIMITED_PLAYS;

    @Builder.Default
    private Integer defaultPlayLimit = 10;

    @OneToMany(mappedBy = "release", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Fetch(FetchMode.SUBSELECT)
    @Builder.Default
    private List<MusicTrack> tracks = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "PUBLISHED";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
