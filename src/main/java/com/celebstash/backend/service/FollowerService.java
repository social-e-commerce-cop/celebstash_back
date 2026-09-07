package com.celebstash.backend.service;

import com.celebstash.backend.dto.user.FollowUserDTO;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Follower;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.repository.FollowerRepository;
import com.celebstash.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FollowerService {

    private final FollowerRepository followerRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final NotificationService notificationService;

    @Transactional
    public void followUser(Long followingId) {
        User currentUser = null;
        try {
            currentUser = userService.getCurrentUser();
        } catch (Exception e) {
            return;
        }
        if (currentUser == null || currentUser.getId().equals(followingId)) {
            return;
        }

        User following = userRepository.findById(followingId).orElse(null);
        if (following == null) return;

        if (followerRepository.findByFollowerAndFollowing(currentUser, following).isPresent()) {
            return; // Already following, return gracefully
        }

        Follower follower = Follower.builder()
                .follower(currentUser)
                .following(following)
                .build();

        followerRepository.save(follower);
        
        // Trigger notification
        try {
            notificationService.createNotification(
                    following,
                    "New Follower",
                    currentUser.getFullName() + " started following you.",
                    NotificationType.NEW_FOLLOWER,
                    currentUser.getId()
            );
        } catch (Exception e) {
            // Ignore notification failure
        }
    }

    @Transactional
    public void unfollowUser(Long followingId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) return;

        User following = userRepository.findById(followingId).orElse(null);
        if (following == null) return;

        followerRepository.findByFollowerAndFollowing(currentUser, following)
                .ifPresent(followerRepository::delete);
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
        try {
            User currentUser = userService.getCurrentUser();
            if (currentUser == null) return false;
            User target = userRepository.findById(userId).orElse(null);
            if (target == null) return false;
            return followerRepository.findByFollowerAndFollowing(currentUser, target).isPresent();
        } catch (Exception e) {
            return false;
        }
    }

    private String getRelationshipStatus(User currentUser, User targetUser) {
        if (currentUser.getId().equals(targetUser.getId())) return "NONE";
        boolean iAmFollowingThem = followerRepository.findByFollowerAndFollowing(currentUser, targetUser).isPresent();
        boolean theyAreFollowingMe = followerRepository.findByFollowerAndFollowing(targetUser, currentUser).isPresent();
        if (iAmFollowingThem) return "FOLLOWING";
        if (theyAreFollowingMe) return "FOLLOW_BACK";
        return "NONE";
    }

    @Transactional(readOnly = true)
    public List<FollowUserDTO> getFollowers(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        User currentUser = null;
        try {
            currentUser = userService.getCurrentUser();
        } catch (Exception ignored) {}

        final User finalCurrentUser = currentUser;
        return followerRepository.findByFollowing(user).stream()
                .map(f -> {
                    User followerUser = f.getFollower();
                    return FollowUserDTO.builder()
                            .id(followerUser.getId())
                            .fullName(followerUser.getFullName())
                            .username(followerUser.getUsername())
                            .profilePicture(followerUser.getProfilePicture())
                            .accountVerified(followerUser.isAccountVerified())
                            .relationship(getRelationshipStatus(finalCurrentUser, followerUser))
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FollowUserDTO> getFollowing(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        User currentUser = null;
        try {
            currentUser = userService.getCurrentUser();
        } catch (Exception ignored) {}

        final User finalCurrentUser = currentUser;
        return followerRepository.findByFollower(user).stream()
                .map(f -> {
                    User followingUser = f.getFollowing();
                    return FollowUserDTO.builder()
                            .id(followingUser.getId())
                            .fullName(followingUser.getFullName())
                            .username(followingUser.getUsername())
                            .profilePicture(followingUser.getProfilePicture())
                            .accountVerified(followingUser.isAccountVerified())
                            .relationship(getRelationshipStatus(finalCurrentUser, followingUser))
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FollowUserDTO> getSuggestedUsers() {
        User currentUser = null;
        try {
            currentUser = userService.getCurrentUser();
        } catch (Exception ignored) {}

        final User finalCurrentUser = currentUser;
        List<User> allUsers = userRepository.findAll();
        return allUsers.stream()
                .filter(u -> finalCurrentUser == null || !u.getId().equals(finalCurrentUser.getId()))
                .filter(u -> finalCurrentUser == null || followerRepository.findByFollowerAndFollowing(finalCurrentUser, u).isEmpty())
                .limit(10)
                .map(u -> FollowUserDTO.builder()
                        .id(u.getId())
                        .fullName(u.getFullName())
                        .username(u.getUsername())
                        .profilePicture(u.getProfilePicture())
                        .accountVerified(u.isAccountVerified())
                        .relationship(getRelationshipStatus(finalCurrentUser, u))
                        .build())
                .collect(Collectors.toList());
    }
}

