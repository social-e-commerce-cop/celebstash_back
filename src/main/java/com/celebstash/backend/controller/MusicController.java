package com.celebstash.backend.controller;

import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.MusicEntitlement;
import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicTrack;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.AccessPackageType;
import com.celebstash.backend.model.enums.ReleaseType;
import com.celebstash.backend.repository.UserRepository;
import com.celebstash.backend.service.FileStorageService;
import com.celebstash.backend.service.MusicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
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
import java.util.*;

@RestController
@RequestMapping("/api/music")
@RequiredArgsConstructor
@Tag(name = "Music", description = "Unreleased & Exclusive Music Marketplace APIs")
public class MusicController {

    private final MusicService musicService;
    private final FileStorageService fileStorageService;
    private final UserRepository userRepository;

    @GetMapping("/releases")
    @Operation(summary = "Get all music releases", description = "Returns discoverable music releases")
    public ResponseEntity<List<MusicRelease>> getAllReleases() {
        return ResponseEntity.ok(musicService.getAllReleases());
    }

    @GetMapping("/releases/{id}")
    @Operation(summary = "Get music release by ID")
    public ResponseEntity<MusicRelease> getReleaseById(@PathVariable("id") Long id) {
        MusicRelease release = musicService.getReleaseById(id)
                .orElseThrow(() -> new AppException("Release not found", HttpStatus.NOT_FOUND));
        return ResponseEntity.ok(release);
    }

    @PostMapping(value = "/releases/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload unreleased music release", description = "Artist uploads Single, EP, Album, or Multiple-track release with metadata")
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
            @RequestParam(value = "defaultPlayLimit", required = false) Integer defaultPlayLimit,
            @RequestParam(value = "accessType", required = false) String accessTypeStr,
            @RequestParam(value = "releaseStory", required = false) String releaseStory,
            @RequestParam(value = "credits", required = false) String credits,
            @RequestParam("coverArt") MultipartFile coverArt,
            @RequestParam("trackFiles") List<MultipartFile> trackFiles,
            @RequestParam("trackTitles") List<String> trackTitles,
            @RequestParam(value = "trackPrices", required = false) List<BigDecimal> trackPrices,
            @RequestParam(value = "trackProducers", required = false) List<String> trackProducers,
            @RequestParam(value = "trackSongwriters", required = false) List<String> trackSongwriters,
            @RequestParam(value = "trackFeaturedArtists", required = false) List<String> trackFeaturedArtists,
            @RequestParam(value = "trackLyrics", required = false) List<String> trackLyrics,
            @RequestParam(value = "trackStories", required = false) List<String> trackStories,
            @RequestParam(value = "trackExplicits", required = false) List<Boolean> trackExplicits
    ) {
        User currentUser = getRequiredCurrentUser();

        // 6.3 Cover Artwork Validation
        if (coverArt == null || coverArt.isEmpty()) {
            throw new IllegalArgumentException("Cover artwork file is required.");
        }
        if (coverArt.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("Cover artwork file size exceeds maximum limit of 10MB.");
        }
        String coverName = coverArt.getOriginalFilename() != null ? coverArt.getOriginalFilename().toLowerCase() : "";
        if (!coverName.endsWith(".jpg") && !coverName.endsWith(".jpeg") && !coverName.endsWith(".png") && !coverName.endsWith(".webp")) {
            throw new IllegalArgumentException("Unsupported cover artwork format. Allowed formats: JPG, PNG, WEBP.");
        }

        // 6.2 Audio File Validation
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
            String audioName = audioFile.getOriginalFilename() != null ? audioFile.getOriginalFilename().toLowerCase() : "";
            boolean validFormat = audioName.endsWith(".mp3") || audioName.endsWith(".wav") ||
                    audioName.endsWith(".aac") || audioName.endsWith(".flac") ||
                    audioName.endsWith(".m4a") || audioName.endsWith(".ogg");
            if (!validFormat) {
                throw new IllegalArgumentException("Track #" + (i + 1) + " has an unsupported audio format. Allowed formats: MP3, WAV, AAC, FLAC, M4A, OGG.");
            }
        }

        String coverUrl = fileStorageService.storeFile(coverArt);

        ReleaseType releaseType = ReleaseType.SINGLE;
        try { releaseType = ReleaseType.valueOf(releaseTypeStr.toUpperCase()); } catch (Exception ignored) {}

        AccessPackageType accessType = AccessPackageType.LIMITED_PLAYS;
        try { if (accessTypeStr != null) accessType = AccessPackageType.valueOf(accessTypeStr.toUpperCase()); } catch (Exception ignored) {}

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
                .defaultPlayLimit(defaultPlayLimit != null ? defaultPlayLimit : 10)
                .defaultAccessPackageType(accessType)
                .releaseStory(releaseStory)
                .credits(credits)
                .status("PUBLISHED")
                .tracks(new ArrayList<>())
                .build();

        MusicRelease savedRelease = musicService.createRelease(release, currentUser);

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
                    .durationSeconds(180) // Standard default duration estimate
                    .build();

            musicService.addTrackToRelease(savedRelease, track);
        }

        return new ResponseEntity<>(musicService.getReleaseById(savedRelease.getId()).orElse(savedRelease), HttpStatus.CREATED);
    }

    @PostMapping("/purchases")
    @Operation(summary = "Purchase music access package")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<MusicEntitlement> purchaseAccess(@RequestBody PurchaseRequest req) {
        User currentUser = getRequiredCurrentUser();
        AccessPackageType packageType = AccessPackageType.LIMITED_PLAYS;
        if (req.getAccessType() != null) {
            try { packageType = AccessPackageType.valueOf(req.getAccessType().toUpperCase()); } catch (Exception ignored) {}
        }

        MusicEntitlement entitlement = musicService.purchaseAccess(
                currentUser, req.getReleaseId(), req.getTrackId(), packageType, req.getPlayLimit(), req.getAmount()
        );
        return ResponseEntity.ok(entitlement);
    }

    @GetMapping("/my-music")
    @Operation(summary = "Get current user's purchased music library")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<MusicEntitlement>> getMyMusic() {
        User currentUser = getRequiredCurrentUser();
        return ResponseEntity.ok(musicService.getUserEntitlements(currentUser));
    }

    @GetMapping("/tracks/{id}/access")
    @Operation(summary = "Verify user playback access for a track")
    public ResponseEntity<Map<String, Object>> verifyAccess(@PathVariable("id") Long trackId) {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(musicService.verifyPlaybackAccess(currentUser, trackId));
    }

    @PostMapping("/tracks/{id}/consume-play")
    @Operation(summary = "Record play consumption after minimum threshold")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> consumePlay(@PathVariable("id") Long trackId) {
        User currentUser = getRequiredCurrentUser();
        return ResponseEntity.ok(musicService.consumePlay(currentUser, trackId));
    }

    @GetMapping("/tracks/{id}/stream")
    @Operation(summary = "Protected audio range streaming endpoint")
    public ResponseEntity<Resource> streamAudio(
            @PathVariable("id") Long trackId,
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
    public static class PurchaseRequest {
        private Long releaseId;
        private Long trackId;
        private String accessType;
        private Integer playLimit;
        private BigDecimal amount;
    }
}
