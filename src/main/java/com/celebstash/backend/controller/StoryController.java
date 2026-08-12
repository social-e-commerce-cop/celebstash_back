package com.celebstash.backend.controller;

import com.celebstash.backend.dto.StoryRequest;
import com.celebstash.backend.dto.StoryResponse;
import com.celebstash.backend.model.User;
import com.celebstash.backend.service.StoryService;
import com.celebstash.backend.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stories")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class StoryController {

    private final StoryService storyService;
    private final UserService userService;

    @PostMapping
    public ResponseEntity<StoryResponse> createStory(@RequestBody StoryRequest request, Principal principal) {
        User user = userService.getUserFromPrincipal(principal);
        return ResponseEntity.ok(storyService.createStory(user, request));
    }

    @GetMapping("/feed")
    public ResponseEntity<List<StoryResponse>> getFeedStories(Principal principal) {
        User currentUser = principal != null ? userService.getUserFromPrincipal(principal) : null;
        return ResponseEntity.ok(storyService.getActiveFeedStories(currentUser));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<StoryResponse>> getUserActiveStories(@PathVariable Long userId, Principal principal) {
        User currentUser = principal != null ? userService.getUserFromPrincipal(principal) : null;
        return ResponseEntity.ok(storyService.getUserActiveStories(userId, currentUser));
    }

    @PostMapping("/{storyId}/view")
    public ResponseEntity<Map<String, String>> recordView(
            @PathVariable Long storyId,
            @RequestBody(required = false) Map<String, Object> body,
            Principal principal) {
        User viewer = userService.getUserFromPrincipal(principal);
        double duration = body != null && body.containsKey("watchDuration") ? Double.parseDouble(body.get("watchDuration").toString()) : 5.0;
        boolean completed = body != null && body.containsKey("completed") && Boolean.parseBoolean(body.get("completed").toString());

        storyService.recordStoryView(storyId, viewer, duration, completed);
        return ResponseEntity.ok(Map.of("message", "View recorded"));
    }

    @PostMapping("/{storyId}/react")
    public ResponseEntity<Map<String, String>> reactToStory(
            @PathVariable Long storyId,
            @RequestBody Map<String, String> body,
            Principal principal) {
        User user = userService.getUserFromPrincipal(principal);
        String emoji = body.getOrDefault("emoji", "❤️");
        storyService.reactToStory(storyId, user, emoji);
        return ResponseEntity.ok(Map.of("message", "Reaction recorded"));
    }

    @PostMapping("/{storyId}/reply")
    public ResponseEntity<Map<String, String>> replyToStory(
            @PathVariable Long storyId,
            @RequestBody Map<String, String> body,
            Principal principal) {
        User user = userService.getUserFromPrincipal(principal);
        String message = body.getOrDefault("message", "");
        storyService.replyToStory(storyId, user, message);
        return ResponseEntity.ok(Map.of("message", "Reply sent successfully"));
    }

    @DeleteMapping("/{storyId}")
    public ResponseEntity<Map<String, String>> deleteStory(@PathVariable Long storyId, Principal principal) {
        User user = userService.getUserFromPrincipal(principal);
        storyService.deleteStory(storyId, user);
        return ResponseEntity.ok(Map.of("message", "Story deleted successfully"));
    }

    @GetMapping("/archive")
    public ResponseEntity<List<StoryResponse>> getArchive(Principal principal) {
        User user = userService.getUserFromPrincipal(principal);
        return ResponseEntity.ok(storyService.getUserArchivedStories(user));
    }
}