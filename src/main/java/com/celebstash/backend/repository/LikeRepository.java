package com.celebstash.backend.repository;

import com.celebstash.backend.model.Like;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.LikeableType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserAndLikeableTypeAndLikeableId(User user, LikeableType likeableType, Long likeableId);

    @Query("SELECT COUNT(l) > 0 FROM Like l WHERE l.user.id = :userId AND l.likeableType = :likeableType AND l.likeableId = :likeableId")
    boolean existsByUserIdAndLikeableTypeAndLikeableId(@Param("userId") Long userId, @Param("likeableType") LikeableType likeableType, @Param("likeableId") Long likeableId);

    long countByLikeableTypeAndLikeableId(LikeableType likeableType, Long likeableId);

    Optional<Like> findByUserAndLikeableTypeAndLikeableId(User user, LikeableType likeableType, Long likeableId);

    List<Like> findAllByLikeableTypeAndLikeableId(LikeableType likeableType, Long likeableId);

    @Query("SELECT l FROM Like l JOIN FETCH l.user WHERE l.likeableType = com.celebstash.backend.model.enums.LikeableType.POST AND l.likeableId = :postId ORDER BY l.createdAt DESC")
    List<Like> findRecentLikesByPost(@Param("postId") Long postId, org.springframework.data.domain.Pageable pageable);

    void deleteAllByLikeableTypeAndLikeableId(LikeableType likeableType, Long likeableId);

    /**
     * Inserts the like unless the same user already liked the same item. Returns 1 when a row was
     * written, 0 when it already existed. Backed by the unique index on (user_id, likeable_type,
     * likeable_id), so concurrent requests cannot create a duplicate.
     */
    @Modifying
    @Query(value = "INSERT INTO likes (user_id, likeable_type, likeable_id, created_at) " +
            "VALUES (:userId, :likeableType, :likeableId, :createdAt) ON CONFLICT DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("userId") Long userId,
                       @Param("likeableType") String likeableType,
                       @Param("likeableId") Long likeableId,
                       @Param("createdAt") java.time.LocalDateTime createdAt);

    void deleteByUserAndLikeableTypeAndLikeableId(User user, LikeableType likeableType, Long likeableId);

    @Modifying
    @Query("DELETE FROM Like l WHERE l.user.id = :userId AND l.likeableType = :likeableType AND l.likeableId = :likeableId")
    void deleteByUserIdAndLikeableTypeAndLikeableId(@Param("userId") Long userId, @Param("likeableType") LikeableType likeableType, @Param("likeableId") Long likeableId);
}
