package com.celebstash.backend.repository;

import com.celebstash.backend.model.Post;
import com.celebstash.backend.model.PostRepost;
import com.celebstash.backend.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PostRepostRepository extends JpaRepository<PostRepost, Long> {

    boolean existsByUserAndPost(User user, Post post);

    @Query("SELECT COUNT(pr) > 0 FROM PostRepost pr WHERE pr.user.id = :userId AND pr.post.id = :postId")
    boolean existsByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    Page<PostRepost> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    @Query(
        value = "SELECT pr FROM PostRepost pr JOIN FETCH pr.post p JOIN FETCH p.user WHERE pr.user.id = :userId ORDER BY pr.createdAt DESC",
        countQuery = "SELECT COUNT(pr) FROM PostRepost pr WHERE pr.user.id = :userId"
    )
    Page<PostRepost> findByUserIdWithPostOrderByCreatedAtDesc(@Param("userId") Long userId, Pageable pageable);

    Optional<PostRepost> findByUserAndPost(User user, Post post);

    @Modifying
    @Query("DELETE FROM PostRepost pr WHERE pr.user.id = :userId AND pr.post.id = :postId")
    void deleteByUserIdAndPostId(@Param("userId") Long userId, @Param("postId") Long postId);

    void deleteByUserAndPost(User user, Post post);

    long countByPost(Post post);
}
