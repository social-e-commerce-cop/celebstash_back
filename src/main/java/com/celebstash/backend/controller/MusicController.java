package com.celebstash.backend.controller;

import com.celebstash.backend.dto.music.*;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.*;
import com.celebstash.backend.repository.UserRepository;
import com.celebstash.backend.service.FileStorageService;
import com.celebstash.backend.service.MusicService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
@Tag(name = "Music", description = "Direct-to-Fan Music Marketplace APIs")
public class MusicController {

    private final MusicService musicService;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/releases")
    @Operation(summary = "Get all discoverable published music releases")
    public ResponseEntity<List<MusicRelease>> getAllReleases() {
        return ResponseEntity.ok(musicService.getAllReleases());
    }

    @GetMapping("/releases/{id}")
    @Operation(summary = "Get music release detail with dynamic benefits and authorization state")
    public ResponseEntity<ReleaseDetailResponse> getReleaseById(@PathVariable("id") Long id) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(musicService.getReleaseDetail(id, currentUser));
    }

    @PostMapping(value = "/releases/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload and create music release (Draft or Published)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MusicRelease> uploadRelease(
            @RequestParam("title") String title,
            @RequestParam("releaseType") String releaseTypeStr,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "genre", required = false) String genre,
            @RequestParam(value = "subgenre", required = false) String subgenre,
            @RequestParam(value = "featuredArtists", required = false) String featuredArtists,
            @RequestParam(value = "language", required = false) String language,
            @RequestParam(value = "countryOfOrigin", required = false) String countryOfOrigin,
            @RequestParam(value = "isExplicit", required = false) Boolean isExplicit,
            @RequestParam(value = "copyrightInfo", required = false) String copyrightInfo,
            @RequestParam(value = "availabilityStatus", required = false) String availabilityStatus,
            @RequestParam(value = "albumPrice", required = false) BigDecimal albumPrice,
            @RequestParam(value = "status", required = false, defaultValue = "DRAFT") String status,
            @RequestParam(value = "releaseStory", required = false) String releaseStory,
            @RequestParam(value = "credits", required = false) String credits,
            @RequestParam(value = "earlyAccessDate", required = false) String earlyAccessDateStr,
            @RequestParam(value = "releaseDate", required = false) String releaseDateStr,
            @RequestParam(value = "downloadAllowed", required = false, defaultValue = "true") Boolean downloadAllowed,
            @RequestParam(value = "connectedEventId", required = false) Long connectedEventId,
            @RequestParam(value = "connectedProductIds", required = false) String connectedProductIds,
            @RequestParam(value = "benefitsJson", required = false) String benefitsJson,
            @RequestParam(value = "exclusiveContentsJson", required = false) String exclusiveContentsJson,
            @RequestParam("coverArt") MultipartFile coverArt,
            @RequestParam("trackFiles") List<MultipartFile> trackFiles,
            @RequestParam("trackTitles") List<String> trackTitles,
            @RequestParam(value = "trackPrices", required = false) List<BigDecimal> trackPrices,
            @RequestParam(value = "trackProducers", required = false) List<String> trackProducers,
            @RequestParam(value = "trackSongwriters", required = false) List<String> trackSongwriters,
            @RequestParam(value = "trackFeaturedArtists", required = false) List<String> trackFeaturedArtists,
            @RequestParam(value = "trackLyrics", required = false) List<String> trackLyrics,
            @RequestParam(value = "trackStories", required = false) List<String> trackStories,
            @RequestParam(value = "trackExplicits", required = false) List<Boolean> trackExplicits,
            @RequestParam(value = "trackIsBonus", required = false) List<Boolean> trackIsBonus
    ) {
        User currentUser = getRequiredCurrentUser();
        if (currentUser.getRole() != Role.ARTIST && currentUser.getRole() != Role.ADMIN) {
            throw new AppException("Only verified artists are permitted to publish music releases.", HttpStatus.FORBIDDEN);
        }

        // Cover Artwork Validation
        if (coverArt == null || coverArt.isEmpty()) {
            throw new IllegalArgumentException("Cover artwork file is required.");
        }
        if (coverArt.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Cover artwork file size exceeds maximum limit of 10MB.");
        }

        // Audio Track Validation
        if (trackFiles == null || trackFiles.isEmpty()) {
            throw new IllegalArgumentException("At least one audio track file is required.");
        }
        for (int i = 0; i < trackFiles.size(); i++) {
            MultipartFile audioFile = trackFiles.get(i);
            if (audioFile == null || audioFile.isEmpty()) {
                throw new IllegalArgumentException("Track #" + (i + 1) + " audio file is empty or corrupted.");
            }
            if (audioFile.getSize() > 100 * 1024 * 1024) {
                throw new IllegalArgumentException("Track #" + (i + 1) + " audio file size exceeds maximum 100MB limit.");
            }
        }

        String coverUrl = fileStorageService.storeFile(coverArt);

        ReleaseType releaseType = ReleaseType.SINGLE;
        try { releaseType = ReleaseType.valueOf(releaseTypeStr.toUpperCase()); } catch (Exception ignored) {}

        LocalDateTime earlyAccessDate = null;
        if (earlyAccessDateStr != null && !earlyAccessDateStr.trim().isEmpty()) {
            try { earlyAccessDate = LocalDateTime.parse(earlyAccessDateStr); } catch (Exception ignored) {}
        }

        LocalDateTime releaseDate = null;
        if (releaseDateStr != null && !releaseDateStr.trim().isEmpty()) {
            try { releaseDate = LocalDateTime.parse(releaseDateStr); } catch (Exception ignored) {}
        }

        List<ReleaseBenefitDto> benefits = new ArrayList<>();
        if (benefitsJson != null && !benefitsJson.trim().isEmpty()) {
            try {
                benefits = objectMapper.readValue(benefitsJson, new TypeReference<List<ReleaseBenefitDto>>() {});
            } catch (Exception ex) {
                log.warn("Could not parse benefitsJson: {}", ex.getMessage());
            }
        }

        List<MusicExclusiveContentDto> exclusiveContents = new ArrayList<>();
        if (exclusiveContentsJson != null && !exclusiveContentsJson.trim().isEmpty()) {
            try {
                exclusiveContents = objectMapper.readValue(exclusiveContentsJson, new TypeReference<List<MusicExclusiveContentDto>>() {});
            } catch (Exception ex) {
                log.warn("Could not parse exclusiveContentsJson: {}", ex.getMessage());
            }
        }

        MusicRelease release = MusicRelease.builder()
                .title(title)
                .releaseType(releaseType)
                .coverArtUrl(coverUrl)
                .description(description)
                .genre(genre)
                .subgenre(subgenre)
                .featuredArtists(featuredArtists)
                .language(language)
                .countryOfOrigin(countryOfOrigin)
                .isExplicit(isExplicit != null && isExplicit)
                .copyrightInfo(copyrightInfo)
                .availabilityStatus(availabilityStatus != null && !availabilityStatus.isEmpty() ? availabilityStatus.toUpperCase() : "UNRELEASED")
                .albumPrice(albumPrice != null ? albumPrice : BigDecimal.valueOf(9.99))
                .status(status != null ? status.toUpperCase() : "DRAFT")
                .releaseStory(releaseStory)
                .credits(credits)
                .earlyAccessDate(earlyAccessDate)
                .releaseDate(releaseDate)
                .downloadAllowed(downloadAllowed != null && downloadAllowed)
                .connectedEventId(connectedEventId)
                .connectedProductIds(connectedProductIds)
                .tracks(new ArrayList<>())
                .benefits(new ArrayList<>())
                .exclusiveContents(new ArrayList<>())
                .build();

        MusicRelease savedRelease = musicService.createRelease(release, benefits, exclusiveContents, currentUser);

        for (int i = 0; i < trackFiles.size(); i++) {
            MultipartFile file = trackFiles.get(i);
            String trackTitle = (trackTitles != null && i < trackTitles.size()) ? trackTitles.get(i) : "Track " + (i + 1);
            BigDecimal price = (trackPrices != null && i < trackPrices.size()) ? trackPrices.get(i) : BigDecimal.valueOf(1.99);
            String producer = (trackProducers != null && i < trackProducers.size()) ? trackProducers.get(i) : null;
            String songwriter = (trackSongwriters != null && i < trackSongwriters.size()) ? trackSongwriters.get(i) : null;
            String trFeatured = (trackFeaturedArtists != null && i < trackFeaturedArtists.size()) ? trackFeaturedArtists.get(i) : null;
            String lyrics = (trackLyrics != null && i < trackLyrics.size()) ? trackLyrics.get(i) : null;
            String trStory = (trackStories != null && i < trackStories.size()) ? trackStories.get(i) : null;
            boolean trExplicit = (trackExplicits != null && i < trackExplicits.size()) && Boolean.TRUE.equals(trackExplicits.get(i));
            boolean isBonus = (trackIsBonus != null && i < trackIsBonus.size()) && Boolean.TRUE.equals(trackIsBonus.get(i));

            String audioPath = fileStorageService.storeFile(file);

            MusicTrack track = MusicTrack.builder()
                    .title(trackTitle)
                    .trackNumber(i + 1)
                    .audioFileUrl(audioPath)
                    .price(price)
                    .producer(producer)
                    .songwriter(songwriter)
                    .featuredArtists(trFeatured)
                    .lyrics(lyrics)
                    .trackStory(trStory)
                    .isExplicit(trExplicit)
                    .isBonusTrack(isBonus)
                    .durationSeconds(180)
                    .previewDurationSeconds(5)
                    .allowDownload(downloadAllowed != null && downloadAllowed)
                    .build();

            musicService.addTrackToRelease(savedRelease, track);
        }

        return new ResponseEntity<>(musicService.getReleaseById(savedRelease.getId()).orElse(savedRelease), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update release metadata, benefits, or configurations")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MusicRelease> updateRelease(
            @PathVariable("id") Long releaseId,
            @RequestBody UpdateReleaseRequest req
    ) {
        User currentUser = getRequiredCurrentUser();
        MusicRelease updated = MusicRelease.builder()
                .title(req.getTitle())
                .description(req.getDescription())
                .coverArtUrl(req.getCoverArtUrl())
                .releaseType(req.getReleaseType())
                .genre(req.getGenre())
                .subgenre(req.getSubgenre())
                .albumPrice(req.getAlbumPrice())
                .downloadAllowed(req.isDownloadAllowed())
                .connectedEventId(req.getConnectedEventId())
                .connectedProductIds(req.getConnectedProductIds())
                .releaseStory(req.getReleaseStory())
                .credits(req.getCredits())
                .copyrightInfo(req.getCopyrightInfo())
                .availabilityStatus(req.getAvailabilityStatus())
                .build();

        return ResponseEntity.ok(musicService.updateRelease(releaseId, updated, req.getBenefits(), req.getExclusiveContents(), currentUser));
    }

    @PostMapping("/releases/{id}/publish")
    @Operation(summary = "Publish a draft release directly to fans")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MusicRelease> publishRelease(@PathVariable("id") Long releaseId) {
        User currentUser = getRequiredCurrentUser();
        return ResponseEntity.ok(musicService.publishRelease(releaseId, currentUser));
    }

    @PostMapping("/releases/{id}/access")
    @Operation(summary = "Purchase release Access via wallet or give as gift")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MusicAccess> purchaseAccess(
            @PathVariable("id") Long releaseId,
            @RequestBody PurchaseAccessDto req
    ) {
        User currentUser = getRequiredCurrentUser();
        req.setReleaseId(releaseId);
        return ResponseEntity.ok(musicService.purchaseReleaseAccess(currentUser, req));
    }

    @PostMapping("/releases/{id}/waitlist")
    @Operation(summary = "Join upcoming release waitlist")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MusicWaitlist> joinWaitlist(@PathVariable("id") Long releaseId) {
        User currentUser = getRequiredCurrentUser();
        return ResponseEntity.ok(musicService.joinWaitlist(currentUser, releaseId));
    }

    @GetMapping("/my-music")
    @Operation(summary = "Get user's purchased music library")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<MusicRelease>> getMyMusic() {
        User currentUser = getRequiredCurrentUser();
        return ResponseEntity.ok(musicService.getUserPurchasedReleases(currentUser));
    }

    @GetMapping("/releases/{id}/exclusive-content")
    @Operation(summary = "Get exclusive media content (protected for Access holders)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<MusicExclusiveContentDto>> getExclusiveContent(@PathVariable("id") Long releaseId) {
        User currentUser = getRequiredCurrentUser();
        return ResponseEntity.ok(musicService.getExclusiveContents(currentUser, releaseId));
    }

    @GetMapping("/tracks/{id}/access")
    @Operation(summary = "Verify playback access status for a track")
    public ResponseEntity<Map<String, Object>> verifyAccess(@PathVariable("id") Long trackId) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(musicService.verifyPlaybackAccess(currentUser, trackId));
    }

    @GetMapping("/tracks/{id}/stream")
    @Operation(summary = "Protected audio streaming endpoint (5s preview without Access; full audio with Access)")
    public ResponseEntity<Resource> streamAudio(
            @PathVariable("id") Long trackId,
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader
    ) {
        MusicTrack track = musicService.getTrackById(trackId)
                .orElseThrow(() -> new AppException("Track not found", HttpStatus.NOT_FOUND));

        User currentUser = getCurrentUser();
        Map<String, Object> access = musicService.verifyPlaybackAccess(currentUser, trackId);
        boolean hasFullAccess = (boolean) access.get("hasFullAccess");

        Resource audioResource = fileStorageService.loadFileAsResource(track.getAudioFileUrl());

        if (!hasFullAccess) {
            // Clip audio to 5-second preview
            try (InputStream is = audioResource.getInputStream()) {
                byte[] allBytes = is.readAllBytes();
                int previewLength = Math.min(allBytes.length, 5 * 64 * 1024 / 8); // Approx 5s chunk
                byte[] previewBytes = Arrays.copyOfRange(allBytes, 0, previewLength);
                ByteArrayResource previewResource = new ByteArrayResource(previewBytes);

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType("audio/mpeg"))
                        .contentLength(previewBytes.length)
                        .header("X-Preview-Only", "true")
                        .body(previewResource);
            } catch (Exception ex) {
                return ResponseEntity.ok().contentType(MediaType.parseMediaType("audio/mpeg")).body(audioResource);
            }
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header("Accept-Ranges", "bytes")
                .header("X-Preview-Only", "false")
                .body(audioResource);
    }

    @GetMapping("/tracks/{id}/download")
    @Operation(summary = "Download track audio file (enforces Download Access benefit)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Resource> downloadTrack(@PathVariable("id") Long trackId) {
        User currentUser = getRequiredCurrentUser();
        if (!musicService.canDownloadTrack(currentUser, trackId)) {
            throw new AppException("Download permission denied. This release does not include Download Access or your account does not hold active Access.", HttpStatus.FORBIDDEN);
        }

        MusicTrack track = musicService.getTrackById(trackId)
                .orElseThrow(() -> new AppException("Track not found", HttpStatus.NOT_FOUND));

        Resource resource = fileStorageService.loadFileAsResource(track.getAudioFileUrl());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("audio/mpeg"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + track.getTitle().replaceAll("[^a-zA-Z0-9.-]", "_") + ".mp3\"")
                .body(resource);
    }

    @GetMapping("/artist/studio")
    @Operation(summary = "Get artist studio direct-to-fan metrics and releases overview")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<ArtistStudioStatsDto> getArtistStudio() {
        User currentUser = getRequiredCurrentUser();
        if (currentUser.getRole() != Role.ARTIST && currentUser.getRole() != Role.ADMIN) {
            throw new AppException("Artist studio access restricted to verified artists", HttpStatus.FORBIDDEN);
        }
        return ResponseEntity.ok(musicService.getArtistStudioStats(currentUser));
    }

    @GetMapping("/artist/releases")
    @Operation(summary = "Get artist releases (filtered by DRAFT or PUBLISHED)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<MusicRelease>> getArtistReleases(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "artistId", required = false) Long artistId
    ) {
        User targetArtist = null;
        if (artistId != null) {
            targetArtist = userRepository.findById(artistId)
                    .orElseThrow(() -> new AppException("Artist not found", HttpStatus.NOT_FOUND));
            // For other users viewing an artist's public releases, only allow PUBLISHED
            User currentUser = getCurrentUser();
            if (currentUser == null || !currentUser.getId().equals(artistId)) {
                return ResponseEntity.ok(musicService.getArtistReleases(targetArtist, "PUBLISHED"));
            }
        } else {
            targetArtist = getRequiredCurrentUser();
        }

        return ResponseEntity.ok(musicService.getArtistReleases(targetArtist, status));
    }

    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null || !(authentication.getPrincipal() instanceof User)) {
            return null;
        }
        User userDetails = (User) authentication.getPrincipal();
        return userRepository.findById(userDetails.getId()).orElse(userDetails);
    }

    private User getRequiredCurrentUser() {
        User user = getCurrentUser();
        if (user == null) {
            throw new AppException("Authentication required", HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    @Data
    public static class UpdateReleaseRequest {
        private String title;
        private String description;
        private String coverArtUrl;
        private ReleaseType releaseType;
        private String genre;
        private String subgenre;
        private BigDecimal albumPrice;
        private boolean downloadAllowed;
        private Long connectedEventId;
        private String connectedProductIds;
        private String releaseStory;
        private String credits;
        private String copyrightInfo;
        private String availabilityStatus;
        private List<ReleaseBenefitDto> benefits;
        private List<MusicExclusiveContentDto> exclusiveContents;
    }
}
