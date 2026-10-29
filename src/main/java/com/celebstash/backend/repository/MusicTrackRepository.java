package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MusicTrackRepository extends JpaRepository<MusicTrack, Long> {
    List<MusicTrack> findByRelease(MusicRelease release);

    /** True when the stored file is a track's protected audio (stored as a bare name or a URL ending in it). */
    @Query("SELECT COUNT(t) > 0 FROM MusicTrack t WHERE t.audioFileUrl = :fileName OR t.audioFileUrl LIKE CONCAT('%/', :fileName)")
    boolean isProtectedAudioFile(@Param("fileName") String fileName);
}
