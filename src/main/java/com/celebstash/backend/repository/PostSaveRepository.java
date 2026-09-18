package com.celebstash.backend.repository;

import com.celebstash.backend.model.Post;
import com.celebstash.backend.model.PostSave;
import com.celebstash.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostSaveRepository extends JpaRepository<PostSave, Long> {

    boolean existsByUserAndPost(User user, Post post);

    @Query("SELECT COUNT(ps) > 0 FROM PostSave ps WHERE ps.user.id = :userId AND ps.post.id = :postId")
    boolean existsByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    Page<PostSave> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    @Query(
        value = "SELECT ps FROM PostSave ps JOIN FETCH ps.post p JOIN FETCH p.user WHERE ps.user.id = :userId ORDER BY ps.createdAt DESC",
        countQuery = "SELECT COUNT(ps) FROM PostSave ps WHERE ps.user.id = :userId"
    )
    Page<PostSave> findByUserIdWithPostOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    Optional<PostSave> findByUserAndPost(User user, Post post);

    @Modifying
    @Query("DELETE FROM PostSave ps WHERE ps.user.id = :userId AND ps.post.id = :postId")
    void deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    void deleteByUserAndPost(User user, Post post);

    long countByPost(Post post);
}
