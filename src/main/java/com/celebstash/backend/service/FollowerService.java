package com.celebstash.backend.service;

import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Follower;
import com.celebstash.backend.model.User;
import com.celebstash.backend.repository.FollowerRepository;
import com.celebstash.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class FollowerService {

    private final FollowerRepository followerRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    @Transactional
    public void followUser(Long followingId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser.getId().equals(followingId)) {
            throw new AppException("You cannot follow yourself", HttpStatus.BAD_REQUEST);
        }

        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new AppException("User to follow not found", HttpStatus.NOT_FOUND));

        followerRepository.findByFollowerAndFollowing(currentUser, following).ifPresent(f -> {
            throw new AppException("You are already following this user", HttpStatus.BAD_REQUEST);
        });

        Follower follower = Follower.builder()
                .follower(currentUser)
                .following(following)
                .build();

        followerRepository.save(follower);
    }

    @Transactional
    public void unfollowUser(Long followingId) {
        User currentUser = userService.getCurrentUser();
        User following = userRepository.findById(followingId)
                .orElseThrow(() -> new AppException("User to unfollow not found", HttpStatus.NOT_FOUND));

        Follower follower = followerRepository.findByFollowerAndFollowing(currentUser, following)
                .orElseThrow(() -> new AppException("You are not following this user", HttpStatus.BAD_REQUEST));

        followerRepository.delete(follower);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getFollowCounts(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        long followersCount = followerRepository.countByFollowing(user);
        long followingCount = followerRepository.countByFollower(user);

        Map<String, Object> counts = new HashMap<>();
        counts.put("followersCount", followersCount);
        counts.put("followingCount", followingCount);
        return counts;
    }

    @Transactional(readOnly = true)
    public boolean isFollowing(Long userId) {
        User currentUser = userService.getCurrentUser();
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        return followerRepository.findByFollowerAndFollowing(currentUser, target).isPresent();
    }
}
