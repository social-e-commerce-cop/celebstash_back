package com.celebstash.backend.repository;

import com.celebstash.backend.model.Story;
import com.celebstash.backend.model.StoryReaction;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StoryReactionRepository extends JpaRepository<StoryReaction, Long> {
    List<StoryReaction> findByStoryOrderByCreatedAtDesc(Story story);
    Optional<StoryReaction> findByStoryAndUser(Story story, User user);
}
