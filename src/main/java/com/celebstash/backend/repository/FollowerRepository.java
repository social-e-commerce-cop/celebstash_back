package com.celebstash.backend.repository;

import com.celebstash.backend.model.Follower;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowerRepository extends JpaRepository<Follower, Long> {
    Optional<Follower> findByFollowerAndFollowing(User follower, User following);
    long countByFollower(User follower);
    long countByFollowing(User following);
    List<Follower> findByFollower(User follower);
    List<Follower> findByFollowing(User following);

    /** Ids of the accounts a user follows, without loading the User rows. */
    @org.springframework.data.jpa.repository.Query("SELECT f.following.id FROM Follower f WHERE f.follower.id = :followerId")
    List<Long> findFollowingIds(@org.springframework.data.repository.query.Param("followerId") Long followerId);
}
