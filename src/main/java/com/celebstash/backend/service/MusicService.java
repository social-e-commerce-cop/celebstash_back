package com.celebstash.backend.service;

import com.celebstash.backend.model.MusicEntitlement;
import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicTrack;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.AccessPackageType;
import com.celebstash.backend.repository.MusicEntitlementRepository;
import com.celebstash.backend.repository.MusicReleaseRepository;
import com.celebstash.backend.repository.MusicTrackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MusicService {

    private final MusicReleaseRepository releaseRepository;
    private final MusicTrackRepository trackRepository;
    private final MusicEntitlementRepository entitlementRepository;
    private final FileStorageService fileStorageService;

    public List<MusicRelease> getAllReleases() {
        return releaseRepository.findAllByOrderByCreatedAtDesc();
    }

    public Optional<MusicRelease> getReleaseById(Long id) {
        return releaseRepository.findById(id);
    }

    public Optional<MusicTrack> getTrackById(Long id) {
        return trackRepository.findById(id);
    }

    public List<MusicRelease> getArtistReleases(User artist) {
        return releaseRepository.findByArtistOrderByCreatedAtDesc(artist);
    }

    @Transactional
    public MusicRelease createRelease(MusicRelease release, User artist) {
        release.setArtist(artist);
        release.setStatus("PUBLISHED");
        return releaseRepository.save(release);
    }

    @Transactional
    public MusicTrack addTrackToRelease(MusicRelease release, MusicTrack track) {
        track.setRelease(release);
        return trackRepository.save(track);
    }

    @Transactional
    public MusicEntitlement purchaseAccess(User buyer, Long releaseId, Long trackId, AccessPackageType accessType, Integer playLimit, BigDecimal price) {
        MusicRelease release = releaseRepository.findById(releaseId)
                .orElseThrow(() -> new IllegalArgumentException("Release not found"));

        MusicTrack track = null;
        if (trackId != null) {
            track = trackRepository.findById(trackId)
                    .orElseThrow(() -> new IllegalArgumentException("Track not found"));
        }

        AccessPackageType chosenAccess = accessType != null ? accessType : release.getDefaultAccessPackageType();
        boolean isPerm = chosenAccess == AccessPackageType.PERMANENT_STREAMING;
        int grantedPlays = isPerm ? 999999 : (playLimit != null ? playLimit : (release.getDefaultPlayLimit() != null ? release.getDefaultPlayLimit() : 10));

        Optional<MusicEntitlement> existingOpt = track != null 
                ? entitlementRepository.findByUserAndTrack(buyer, track)
                : entitlementRepository.findByUserAndReleaseAndTrackIsNull(buyer, release);

        MusicEntitlement entitlement;
        if (existingOpt.isPresent()) {
            entitlement = existingOpt.get();
            if (isPerm) {
                entitlement.setPermanent(true);
                entitlement.setAccessType(AccessPackageType.PERMANENT_STREAMING);
                entitlement.setPlaysRemaining(999999);
            } else {
                entitlement.setPlaysGranted(entitlement.getPlaysGranted() + grantedPlays);
                entitlement.setPlaysRemaining(entitlement.getPlaysRemaining() + grantedPlays);
            }
            entitlement.setAmountPaid(entitlement.getAmountPaid() != null ? entitlement.getAmountPaid().add(price != null ? price : BigDecimal.ZERO) : price);
        } else {
            entitlement = MusicEntitlement.builder()
                    .user(buyer)
                    .release(release)
                    .track(track)
                    .accessType(chosenAccess)
                    .playsGranted(grantedPlays)
                    .playsUsed(0)
                    .playsRemaining(grantedPlays)
                    .isPermanent(isPerm)
                    .amountPaid(price)
                    .purchasedAt(LocalDateTime.now())
                    .build();
        }

        return entitlementRepository.save(entitlement);
    }

    public Map<String, Object> verifyPlaybackAccess(User user, Long trackId) {
        Map<String, Object> response = new HashMap<>();
        if (user == null || trackId == null) {
            response.put("hasFullAccess", false);
            response.put("remainingPlays", 0);
            response.put("isPermanent", false);
            response.put("maxDurationSeconds", 5);
            return response;
        }

        MusicTrack track = trackRepository.findById(trackId).orElse(null);
        if (track == null) {
            response.put("hasFullAccess", false);
            response.put("remainingPlays", 0);
            response.put("isPermanent", false);
            response.put("maxDurationSeconds", 5);
            return response;
        }

        if (track.getRelease() != null && track.getRelease().getArtist() != null &&
            track.getRelease().getArtist().getId().equals(user.getId())) {
            response.put("hasFullAccess", true);
            response.put("remainingPlays", 999999);
            response.put("isPermanent", true);
            response.put("maxDurationSeconds", null);
            return response;
        }

        Optional<MusicEntitlement> trackEntitlement = entitlementRepository.findByUserAndTrack(user, track);
        if (trackEntitlement.isPresent()) {
            MusicEntitlement ent = trackEntitlement.get();
            if (ent.isPermanent() || ent.getPlaysRemaining() > 0) {
                response.put("hasFullAccess", true);
                response.put("remainingPlays", ent.isPermanent() ? 999999 : ent.getPlaysRemaining());
                response.put("isPermanent", ent.isPermanent());
                response.put("maxDurationSeconds", null);
                return response;
            }
        }

        if (track.getRelease() != null) {
            Optional<MusicEntitlement> albumEntitlement = entitlementRepository.findByUserAndReleaseAndTrackIsNull(user, track.getRelease());
            if (albumEntitlement.isPresent()) {
                MusicEntitlement ent = albumEntitlement.get();
                if (ent.isPermanent() || ent.getPlaysRemaining() > 0) {
                    response.put("hasFullAccess", true);
                    response.put("remainingPlays", ent.isPermanent() ? 999999 : ent.getPlaysRemaining());
                    response.put("isPermanent", ent.isPermanent());
                    response.put("maxDurationSeconds", null);
                    return response;
                }
            }
        }

        response.put("hasFullAccess", false);
        response.put("remainingPlays", 0);
        response.put("isPermanent", false);
        response.put("maxDurationSeconds", 5);
        return response;
    }

    @Transactional
    public Map<String, Object> consumePlay(User user, Long trackId) {
        Map<String, Object> access = verifyPlaybackAccess(user, trackId);
        boolean hasAccess = (boolean) access.get("hasFullAccess");
        boolean isPermanent = (boolean) access.get("isPermanent");

        if (!hasAccess || isPermanent) {
            return access;
        }

        MusicTrack track = trackRepository.findById(trackId).orElse(null);
        if (track == null) return access;

        Optional<MusicEntitlement> trackEnt = entitlementRepository.findByUserAndTrack(user, track);
        if (trackEnt.isPresent() && trackEnt.get().getPlaysRemaining() > 0 && !trackEnt.get().isPermanent()) {
            MusicEntitlement ent = trackEnt.get();
            ent.setPlaysRemaining(ent.getPlaysRemaining() - 1);
            ent.setPlaysUsed(ent.getPlaysUsed() + 1);
            if (ent.getFirstPlayedAt() == null) ent.setFirstPlayedAt(LocalDateTime.now());
            ent.setLastPlayedAt(LocalDateTime.now());
            entitlementRepository.save(ent);

            access.put("remainingPlays", ent.getPlaysRemaining());
            return access;
        }

        if (track.getRelease() != null) {
            Optional<MusicEntitlement> albumEnt = entitlementRepository.findByUserAndReleaseAndTrackIsNull(user, track.getRelease());
            if (albumEnt.isPresent() && albumEnt.get().getPlaysRemaining() > 0 && !albumEnt.get().isPermanent()) {
                MusicEntitlement ent = albumEnt.get();
                ent.setPlaysRemaining(ent.getPlaysRemaining() - 1);
                ent.setPlaysUsed(ent.getPlaysUsed() + 1);
                if (ent.getFirstPlayedAt() == null) ent.setFirstPlayedAt(LocalDateTime.now());
                ent.setLastPlayedAt(LocalDateTime.now());
                entitlementRepository.save(ent);

                access.put("remainingPlays", ent.getPlaysRemaining());
                return access;
            }
        }

        return access;
    }

    public List<MusicEntitlement> getUserEntitlements(User user) {
        return entitlementRepository.findByUserOrderByPurchasedAtDesc(user);
    }
}
