package com.celebstash.backend.repository;

import com.celebstash.backend.model.MusicRelease;
import com.celebstash.backend.model.MusicWaitlist;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MusicWaitlistRepository extends JpaRepository<MusicWaitlist, Long> {
    Optional<MusicWaitlist> findByUserAndRelease(User user, MusicRelease release);
    boolean existsByUserAndRelease(User user, MusicRelease release);
    List<MusicWaitlist> findByRelease(MusicRelease release);
    long countByRelease(MusicRelease release);
}
