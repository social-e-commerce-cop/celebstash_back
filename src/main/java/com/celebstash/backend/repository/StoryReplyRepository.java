package com.celebstash.backend.repository;

import com.celebstash.backend.model.Story;
import com.celebstash.backend.model.StoryReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StoryReplyRepository extends JpaRepository<StoryReply, Long> {
    List<StoryReply> findByStoryOrderByCreatedAtDesc(Story story);
}
