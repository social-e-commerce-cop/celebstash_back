package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicPlay;
import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MusicPlayRepository extends JpaRepository<MusicPlay, Long> {

    long countByRelease(MusicRelease release);

    long countByTrack(MusicTrack track);

    @Query("SELECT COUNT(DISTINCT p.user.id) FROM MusicPlay p WHERE p.release = :release AND p.user IS NOT NULL")
    long countDistinctUsersByRelease(@Param("release") MusicRelease release);

    @Query("SELECT p.user.id, COUNT(p) FROM MusicPlay p WHERE p.release = :release AND p.user IS NOT NULL GROUP BY p.user.id")
    List<Object[]> countPlaysPerUserByRelease(@Param("release") MusicRelease release);

    @Query("SELECT p FROM MusicPlay p WHERE p.release = :release AND p.playedAt >= :since ORDER BY p.playedAt ASC")
    List<MusicPlay> findByReleaseAndPlayedAtAfter(@Param("release") MusicRelease release, @Param("since") LocalDateTime since);

    List<MusicPlay> findByReleaseOrderByPlayedAtAsc(MusicRelease release);
}
