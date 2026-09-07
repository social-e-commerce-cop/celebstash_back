package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicEntitlement;
import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicTrack;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MusicEntitlementRepository extends JpaRepository<MusicEntitlement, Long> {
    List<MusicEntitlement> findByUserOrderByPurchasedAtDesc(User user);
    Optional<MusicEntitlement> findByUserAndTrack(User user, MusicTrack track);
    Optional<MusicEntitlement> findByUserAndReleaseAndTrackIsNull(User user, MusicRelease release);
    List<MusicEntitlement> findByUserAndRelease(User user, MusicRelease release);
}
