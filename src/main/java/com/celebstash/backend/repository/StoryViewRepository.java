package com.celebstash.backend.repository;

import com.celebstash.backend.model.Story;
import com.celebstash.backend.model.StoryView;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoryViewRepository extends JpaRepository<StoryView, Long> {
    Optional<StoryView> findByStoryAndViewer(Story story, User viewer);
    List<StoryView> findByStoryOrderByViewedAtDesc(Story story);
    boolean existsByStoryAndViewer(Story story, User viewer);
}
