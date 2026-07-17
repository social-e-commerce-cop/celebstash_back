package com.celebstash.backend.controller;

import com.celebstash.backend.service.FollowerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/follow")
@RequiredArgsConstructor
@Tag(name = "Follows Graph", description = "User follow/unfollow and follower status tracking APIs")
public class FollowerController {

    private final FollowerService followerService;

    @PostMapping("/{userId}")
    @Operation(summary = "Follow a user", description = "Establishes a follow connection. Connections to ARTISTS are Tribers; to regular USERs are Mates.")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> followUser(@PathVariable Long userId) {
        followerService.followUser(userId);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Unfollow a user", description = "Removes a follow connection")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> unfollowUser(@PathVariable Long userId) {
        followerService.unfollowUser(userId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{userId}/counts")
    @Operation(summary = "Get follow counts", description = "Returns the followers and following count for a specific user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Object>> getFollowCounts(@PathVariable Long userId) {
        return ResponseEntity.ok(followerService.getFollowCounts(userId));
    }

    @GetMapping("/users/{userId}/status")
    @Operation(summary = "Check follow status", description = "Returns true if the logged-in user follows the target user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Boolean>> checkFollowStatus(@PathVariable Long userId) {
        boolean following = followerService.isFollowing(userId);
        return ResponseEntity.ok(Map.of("isFollowing", following));
    }
}
