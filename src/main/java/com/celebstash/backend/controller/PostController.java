package com.celebstash.backend.controller;

import com.celebstash.backend.dto.post.PostRequest;
import com.celebstash.backend.dto.post.PostResponse;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.User;
import com.celebstash.backend.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
@Tag(name = "Posts", description = "Post management APIs")
public class PostController {

    private final PostService postService;

    // ------------------ CREATE POST ------------------
    @PostMapping
    @Operation(summary = "Create a new post", description = "Creates a new post for a product")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody PostRequest request) {
        Long userId = getCurrentUserId();
        return new ResponseEntity<>(postService.createPost(request, userId), HttpStatus.CREATED);
    }

    // ------------------ GET ALL POSTS ------------------
    @GetMapping
    @Operation(summary = "Get all posts", description = "Returns all posts with pagination")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<PostResponse>> getAllPosts(@PageableDefault(size = 10) Pageable pageable) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getAllPosts(userId, pageable));
    }

    // ------------------ GET POST BY ID ------------------
    @GetMapping("/{id}")
    @Operation(summary = "Get post by ID", description = "Returns a post by its ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> getPostById(@PathVariable("id") Long postId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getPostById(postId, userId));
    }

    // ------------------ GET MY POSTS ------------------
    @GetMapping("/my-posts")
    @Operation(summary = "Get my posts", description = "Returns all posts by the current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<PostResponse>> getMyPosts(@PageableDefault(size = 10) Pageable pageable) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getMyPosts(userId, pageable));
    }

    // ------------------ UPDATE POST ------------------
    @PutMapping("/{id}")
    @Operation(summary = "Update post", description = "Updates a post (only the owner can update)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> updatePost(
            @PathVariable("id") Long postId,
            @Valid @RequestBody PostRequest request
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.updatePost(postId, request, userId));
    }

    // ------------------ DELETE POST ------------------
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete post", description = "Deletes a post (only the owner can delete)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deletePost(@PathVariable("id") Long postId) {
        Long userId = getCurrentUserId();
        postService.deletePost(postId, userId);
        return ResponseEntity.noContent().build();
    }

    // ------------------ LIKE POST ------------------
    @PostMapping("/{id}/like")
    @Operation(summary = "Like post", description = "Likes a post")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> likePost(@PathVariable("id") Long postId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.likePost(postId, userId));
    }

    // ------------------ UNLIKE POST ------------------
    @DeleteMapping("/{id}/like")
    @Operation(summary = "Unlike post", description = "Unlikes a post")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> unlikePost(@PathVariable("id") Long postId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.unlikePost(postId, userId));
    }

    // ------------------ HELPER METHOD ------------------
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new AppException("User not authenticated", HttpStatus.UNAUTHORIZED);
        }

        // Cast principal to your User entity since it implements UserDetails
        User user = (User) authentication.getPrincipal();
        return user.getId();
    }
}
