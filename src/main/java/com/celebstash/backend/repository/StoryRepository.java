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

    /** Live stories whose author has one of the given roles, oldest first, author loaded. */
    @Query("SELECT s FROM Story s JOIN FETCH s.user u WHERE s.isArchived = false AND s.expiresAt > :now " +
            "AND u.role IN :roles ORDER BY s.createdAt ASC")
    List<Story> findActiveStoriesByAuthorRoles(@Param("now") LocalDateTime now,
                                               @Param("roles") java.util.Collection<com.celebstash.backend.model.enums.Role> roles);

    /** Stories past their expiry that the cleanup job has not archived yet. */
    @Query("SELECT s FROM Story s WHERE s.isArchived = false AND s.expiresAt <= :now")
    List<Story> findExpiredUnarchived(@Param("now") LocalDateTime now);

    /** Atomic increment, so simultaneous viewers cannot overwrite each other's count. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("UPDATE Story s SET s.viewCount = s.viewCount + 1 WHERE s.id = :storyId")
    int incrementViewCount(@Param("storyId") Long storyId);
}