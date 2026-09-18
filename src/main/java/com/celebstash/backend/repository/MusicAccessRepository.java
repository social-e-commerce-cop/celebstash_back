package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicAccess;
import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.MusicAccessStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MusicAccessRepository extends JpaRepository<MusicAccess, Long> {
    Optional<MusicAccess> findByUserAndRelease(User user, MusicRelease release);
    Optional<MusicAccess> findByUserAndReleaseAndStatus(User user, MusicRelease release, MusicAccessStatus status);
    boolean existsByUserAndReleaseAndStatus(User user, MusicRelease release, MusicAccessStatus status);
    List<MusicAccess> findByUserAndStatusOrderByGrantedAtDesc(User user, MusicAccessStatus status);
    List<MusicAccess> findByUserOrderByGrantedAtDesc(User user);
    long countByReleaseAndStatus(MusicRelease release, MusicAccessStatus status);

    @Query("SELECT ma FROM MusicAccess ma WHERE ma.release.artist = :artist ORDER BY ma.grantedAt DESC")
    List<MusicAccess> findByArtistReleases(@Param("artist") User artist);

    @Query("SELECT COUNT(ma) FROM MusicAccess ma WHERE ma.release.artist = :artist AND ma.status = 'ACTIVE'")
    long countActiveAccessByArtist(@Param("artist") User artist);
}
