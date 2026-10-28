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

    /** Records one view per viewer per story; returns 0 when the viewer had already seen it. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value =
            // user_id duplicates viewer_id: older databases still key this table on (story_id, user_id).
            "INSERT INTO story_views (story_id, viewer_id, user_id, viewed_at, watch_duration, completed) " +
            "VALUES (:storyId, :viewerId, :viewerId, :viewedAt, :watchDuration, :completed) ON CONFLICT DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(@org.springframework.data.repository.query.Param("storyId") Long storyId,
                       @org.springframework.data.repository.query.Param("viewerId") Long viewerId,
                       @org.springframework.data.repository.query.Param("viewedAt") java.time.LocalDateTime viewedAt,
                       @org.springframework.data.repository.query.Param("watchDuration") double watchDuration,
                       @org.springframework.data.repository.query.Param("completed") boolean completed);
}
