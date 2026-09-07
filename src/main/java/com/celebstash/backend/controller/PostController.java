package com.celebstash.backend.controller;

import com.celebstash.backend.dto.comment.CommentRequest;
import com.celebstash.backend.dto.comment.CommentResponse;
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
    @Operation(summary = "Create a new post", description = "Creates a new post (Approved Artists and Admins only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> createPost(@Valid @RequestBody PostRequest request) {
        Long userId = getCurrentUserId();
        return new ResponseEntity<>(postService.createPost(request, userId), HttpStatus.CREATED);
    }

    // ------------------ GET HOME FEED ------------------
    @GetMapping({"", "/feed"})
    @Operation(summary = "Get home feed", description = "Returns feed posts (followed artists + discovery)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<PostResponse>> getFeed(@PageableDefault(size = 10) Pageable pageable) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getHomeFeed(userId, pageable));
    }

    // ------------------ GET DISCOVERY FEED ------------------
    @GetMapping("/discovery")
    @Operation(summary = "Get discovery feed", description = "Returns discovery/trending feed posts")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<PostResponse>> getDiscoveryFeed(@PageableDefault(size = 10) Pageable pageable) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getDiscoveryFeed(userId, pageable));
    }

    // ------------------ GET ARTIST PROFILE POSTS ------------------
    @GetMapping("/user/{targetUserId}")
    @Operation(summary = "Get artist profile posts", description = "Returns posts created by a specific user/artist")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<PostResponse>> getArtistPosts(
            @PathVariable("targetUserId") Long targetUserId,
            @PageableDefault(size = 10) Pageable pageable
    ) {
        Long currentUserId = getCurrentUserId();
        return ResponseEntity.ok(postService.getArtistPosts(targetUserId, currentUserId, pageable));
    }

    // ------------------ GET MY POSTS ------------------
    @GetMapping("/my-posts")
    @Operation(summary = "Get my posts", description = "Returns all posts by the current logged-in artist")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<PostResponse>> getMyPosts(@PageableDefault(size = 10) Pageable pageable) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getMyPosts(userId, pageable));
    }

    // ------------------ GET SAVED POSTS ------------------
    @GetMapping("/saved")
    @Operation(summary = "Get saved posts", description = "Returns saved posts for current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<PostResponse>> getSavedPosts(@PageableDefault(size = 10) Pageable pageable) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getSavedPosts(userId, pageable));
    }

    // ------------------ GET POST BY ID ------------------
    @GetMapping("/{id}")
    @Operation(summary = "Get post by ID", description = "Returns a post by its ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> getPostById(@PathVariable("id") Long postId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getPostById(postId, userId));
    }

    // ------------------ UPDATE POST ------------------
    @PutMapping("/{id}")
    @Operation(summary = "Update post", description = "Updates a post (only owner)")
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
    @Operation(summary = "Delete post", description = "Deletes a post (only owner)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deletePost(@PathVariable("id") Long postId) {
        Long userId = getRequiredCurrentUserId();
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

    // ------------------ GET POST LIKES ------------------
    @GetMapping("/{id}/likes")
    @Operation(summary = "Get users who liked a post", description = "Returns list of users who liked the post")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<java.util.List<com.celebstash.backend.dto.user.UserPublicDTO>> getPostLikes(@PathVariable("id") Long postId) {
        return ResponseEntity.ok(postService.getPostLikes(postId));
    }

    // ------------------ REPOST POST ------------------
    @PostMapping("/{id}/repost")
    @Operation(summary = "Repost post", description = "Reposts a post")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> repostPost(@PathVariable("id") Long postId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.repostPost(postId, userId));
    }

    // ------------------ UNREPOST POST ------------------
    @DeleteMapping("/{id}/repost")
    @Operation(summary = "Unrepost post", description = "Removes repost of a post")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> unrepostPost(@PathVariable("id") Long postId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.unrepostPost(postId, userId));
    }

    // ------------------ SAVE POST ------------------
    @PostMapping("/{id}/save")
    @Operation(summary = "Save post", description = "Saves a post for the current user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> savePost(@PathVariable("id") Long postId) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.savePost(postId, userId));
    }

    // ------------------ UNSAVE POST ------------------
    @DeleteMapping("/{id}/save")
    @Operation(summary = "Unsave post", description = "Removes a saved post")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<PostResponse> unsavePost(@PathVariable("id") Long id) {
        Long userId = getRequiredCurrentUserId();
        return ResponseEntity.ok(postService.unsavePost(id, userId));
    }

    // ------------------ GET COMMENTS ------------------
    @GetMapping("/{id}/comments")
    @Operation(summary = "Get post comments", description = "Returns comments for a specific post")
    public ResponseEntity<Page<CommentResponse>> getComments(
            @PathVariable("id") Long id,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getPostComments(id, pageable, userId));
    }

    // ------------------ ADD COMMENT ------------------
    @PostMapping("/{id}/comments")
    @Operation(summary = "Add a comment", description = "Adds a comment to a post")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommentResponse> addComment(
            @PathVariable("id") Long id,
            @Valid @RequestBody CommentRequest request
    ) {
        Long userId = getRequiredCurrentUserId();
        request.setPostId(id);
        return new ResponseEntity<>(postService.addComment(id, request, userId), HttpStatus.CREATED);
    }

    // ------------------ LIKE COMMENT ------------------
    @PostMapping("/{postId}/comments/{commentId}/like")
    @Operation(summary = "Like a comment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommentResponse> likeComment(
            @PathVariable Long postId, @PathVariable Long commentId) {
        Long userId = getRequiredCurrentUserId();
        return ResponseEntity.ok(postService.likeComment(commentId, userId));
    }

    // ------------------ UNLIKE COMMENT ------------------
    @DeleteMapping("/{postId}/comments/{commentId}/like")
    @Operation(summary = "Unlike a comment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommentResponse> unlikeComment(
            @PathVariable Long postId, @PathVariable Long commentId) {
        Long userId = getRequiredCurrentUserId();
        return ResponseEntity.ok(postService.unlikeComment(commentId, userId));
    }

    // ------------------ EDIT COMMENT ------------------
    @PutMapping("/{postId}/comments/{commentId}")
    @Operation(summary = "Edit a comment (owner only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<CommentResponse> editComment(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest request) {
        Long userId = getRequiredCurrentUserId();
        return ResponseEntity.ok(postService.editComment(commentId, request, userId));
    }

    // ------------------ DELETE COMMENT ------------------
    @DeleteMapping("/{postId}/comments/{commentId}")
    @Operation(summary = "Delete a comment (owner only)")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long postId, @PathVariable Long commentId) {
        Long userId = getRequiredCurrentUserId();
        postService.deleteComment(commentId, userId);
        return ResponseEntity.noContent().build();
    }

    // ------------------ GET REPLIES FOR A COMMENT ------------------
    @GetMapping("/{postId}/comments/{commentId}/replies")
    @Operation(summary = "Get paginated replies for a comment")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Page<CommentResponse>> getCommentReplies(
            @PathVariable Long postId,
            @PathVariable Long commentId,
            @PageableDefault(size = 10) Pageable pageable) {
        Long userId = getCurrentUserId();
        return ResponseEntity.ok(postService.getCommentReplies(commentId, pageable, userId));
    }

    // ------------------ HELPER METHOD ------------------
    private Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null || !(authentication.getPrincipal() instanceof User)) {
            return null;
        }

        User user = (User) authentication.getPrincipal();
        return user.getId();
    }

    private Long getRequiredCurrentUserId() {
        Long userId = getCurrentUserId();
        if (userId == null) {
            throw new AppException("User not authenticated", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }
}
