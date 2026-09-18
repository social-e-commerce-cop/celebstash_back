package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicTrack;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MusicTrackRepository extends JpaRepository<MusicTrack, Long> {
    List<MusicTrack> findByRelease(MusicRelease release);
}
