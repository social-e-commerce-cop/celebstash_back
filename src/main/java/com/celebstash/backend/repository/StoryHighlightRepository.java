package com.celebstash.backend.repository;

import com.celebstash.backend.model.StoryHighlight;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryHighlightRepository extends JpaRepository<StoryHighlight, Long> {
    List<StoryHighlight> findByUserOrderByCreatedAtDesc(User user);
}
