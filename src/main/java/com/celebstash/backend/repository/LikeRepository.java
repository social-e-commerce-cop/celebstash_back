package com.celebstash.backend.repository;

import com.celebstash.backend.model.Like;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.LikeableType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserAndLikeableTypeAndLikeableId(User user, LikeableType likeableType, Long likeableId);

    long countByLikeableTypeAndLikeableId(LikeableType likeableType, Long likeableId);

    Optional<Like> findByUserAndLikeableTypeAndLikeableId(User user, LikeableType likeableType, Long likeableId);

    void deleteAllByLikeableTypeAndLikeableId(LikeableType likeableType, Long likeableId);
}
