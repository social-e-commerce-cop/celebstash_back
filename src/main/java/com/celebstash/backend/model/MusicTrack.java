package com.celebstash.backend.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@Table(name = "music_tracks")
public class MusicTrack {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "release_id", nullable = false)
    @JsonIgnore
    private MusicRelease release;

    @Column(nullable = false)
    private String title;

    private Integer trackNumber;

    @Column(nullable = false)
    private String audioFileUrl;

    private Integer durationSeconds;

    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    private String producer;

    private String songwriter;

    private String featuredArtists;

    @Builder.Default
    private boolean isExplicit = false;

    @Column(length = 4000)
    private String lyrics;

    @Column(length = 2000)
    private String trackStory;
}
