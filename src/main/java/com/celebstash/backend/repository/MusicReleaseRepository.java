package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface MusicReleaseRepository extends JpaRepository<MusicRelease, Long> {
    List<MusicRelease> findByArtistOrderByCreatedAtDesc(User artist);
    List<MusicRelease> findByArtistAndStatusOrderByCreatedAtDesc(User artist, String status);
    List<MusicRelease> findByStatusOrderByCreatedAtDesc(String status);
    List<MusicRelease> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses);
    List<MusicRelease> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
}
