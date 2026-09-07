package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MusicReleaseRepository extends JpaRepository<MusicRelease, Long> {
    List<MusicRelease> findByArtistOrderByCreatedAtDesc(User artist);
    List<MusicRelease> findByStatusOrderByCreatedAtDesc(String status);
    List<MusicRelease> findAllByOrderByCreatedAtDesc();
}
