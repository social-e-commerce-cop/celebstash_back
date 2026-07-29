package com.celebstash.backend.repository;

import com.celebstash.backend.model.ArtistApplication;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ApplicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ArtistApplicationRepository extends JpaRepository<ArtistApplication, Long> {
    Optional<ArtistApplication> findTopByUserOrderByCreatedAtDesc(User user);
    List<ArtistApplication> findByStatusOrderByCreatedAtDesc(ApplicationStatus status);
    List<ArtistApplication> findAllByOrderByCreatedAtDesc();
}
