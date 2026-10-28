package com.celebstash.backend.dto.music;

import com.celebstash.backend.model.MusicRelease;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArtistSongDetailsDto {

    // Song Header & Core Model
    private MusicRelease release;
    private Long id;
    private String title;
    private String artistName;
    private String artistUsername;
    private String coverArtUrl;
    private String status; // PUBLISHED, ARCHIVED, DRAFT
    private Integer durationSeconds;
    private String formattedDuration;
    private String releaseDate;

    // Performance (Basic Song Statistics)
    private long totalPlays;
    private long uniqueListeners;
    private long replays;

    // Simple Listening Activity
    private List<ListeningActivityPointDto> activity7Days;
    private List<ListeningActivityPointDto> activity30Days;
    private List<ListeningActivityPointDto> activityAllTime;

    // Access / Purchases
    private boolean isPaidRelease;
    private long accessGranted;
    private long purchases;

    // Song Information
    private String genre;
    private String subgenre;
    private String releaseType;
    private BigDecimal price;
    private String description;
    private int trackCount;

    // Stories (release-level and per-track)
    private String releaseStory;
    private List<TrackStoryDto> tracks;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TrackStoryDto {
        private Long id;
        private Integer trackNumber;
        private String title;
        private String trackStory;
        private String producer;
        private String songwriter;
        private String featuredArtists;
        private boolean isExplicit;
        private boolean isBonusTrack;
        private Integer durationSeconds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ListeningActivityPointDto {
        private String date;       // e.g. "2026-09-18"
        private String label;      // e.g. "Fri", "Sep 18", or "2026-09"
        private long count;        // number of plays on this day/interval
    }
}
