package com.celebstash.backend.repository;

import com.celebstash.backend.model.Story;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface StoryRepository extends JpaRepository<Story, Long> {

    List<Story> findByUserOrderByCreatedAtDesc(User user);

    @Query("SELECT s FROM Story s WHERE s.isArchived = false AND s.expiresAt > :now ORDER BY s.createdAt DESC")
    List<Story> findAllActiveStories(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM Story s WHERE s.user = :user AND s.isArchived = false AND s.expiresAt > :now ORDER BY s.createdAt ASC")
    List<Story> findActiveStoriesByUser(@Param("user") User user, @Param("now") LocalDateTime now);

    @Query("SELECT s FROM Story s WHERE s.isArchived = true OR s.expiresAt <= :now ORDER BY s.createdAt DESC")
    List<Story> findExpiredStories(@Param("now") LocalDateTime now);

    @Query("SELECT s FROM Story s WHERE s.user = :user AND (s.isArchived = true OR s.expiresAt <= :now) ORDER BY s.createdAt DESC")
    List<Story> findArchivedStoriesByUser(@Param("user") User user, @Param("now") LocalDateTime now);
}