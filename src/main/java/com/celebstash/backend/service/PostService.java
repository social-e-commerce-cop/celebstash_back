package com.celebstash.backend.service;

import com.celebstash.backend.dto.comment.CommentRequest;
import com.celebstash.backend.dto.comment.CommentResponse;
import com.celebstash.backend.dto.post.PostRequest;
import com.celebstash.backend.dto.post.PostResponse;
import com.celebstash.backend.dto.product.ProductResponse;
import com.celebstash.backend.dto.user.UserPublicDTO;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.LikeableType;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.model.enums.PostStatus;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;
    private final CommentRepository commentRepository;
    private final FollowerRepository followerRepository;
    private final NotificationService notificationService;
    private final ArtistApplicationRepository artistApplicationRepository;

    @Transactional
    public PostResponse createPost(PostRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        if (user.getRole() != Role.ARTIST && user.getRole() != Role.ADMIN) {
            user.setRole(Role.ARTIST);
            userRepository.save(user);
        }

        Product product = null;
        if (request.getProductId() != null) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));
        }

        Post post = new Post();
        post.setUser(user);
        post.setDescription(request.getDescription());
        post.setVideoUrl(request.getVideoUrl());
        post.setImageUrls(request.getImageUrls() != null ? request.getImageUrls() : new ArrayList<>());
        post.setProduct(product);
        post.setSponsored(request.isSponsored());
        post.setSponsorName(request.getSponsorName());
        post.setAttachedType(request.getAttachedType());
        post.setAttachedTitle(request.getAttachedTitle());
        post.setAttachedSubtitle(request.getAttachedSubtitle());
        post.setAttachedPrice(request.getAttachedPrice());
        post.setStatus(PostStatus.ACTIVE);
        post.setCreatedAt(LocalDateTime.now());

        Post savedPost;
        try {
            savedPost = postRepository.save(post);
        } catch (Exception e) {
            log.error("Failed to save post for user {}: {}", userId, e.getMessage(), e);
            throw new AppException("Could not save post: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        // Notify followers asynchronously
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                List<Follower> followers = followerRepository.findByFollowing(user);
                for (Follower f : followers) {
                    try {
                        notificationService.createNotification(
                                f.getFollower(),
                                "New Post from " + user.getFullName(),
                                user.getFullName() + " posted: " + (post.getDescription() != null && post.getDescription().length() > 50 
                                        ? post.getDescription().substring(0, 50) + "..." : (post.getDescription() != null ? post.getDescription() : "New update")),
                                NotificationType.STORY_POST_ACTIVITY,
                                savedPost.getId()
                        );
                    } catch (Exception e) {
                        log.error("Failed to send post notification to follower {}: {}", f.getFollower().getId(), e.getMessage());
                    }
                }
            } catch (Exception e) {
                log.error("Failed to notify followers for post {}: {}", savedPost.getId(), e.getMessage());
            }
        });

        return mapToResponse(savedPost, user);
    }

    // ----------------- HOME FEED (FOLLOWED ARTISTS + DISCOVERY) -----------------
    @Transactional(readOnly = true)
    public Page<PostResponse> getHomeFeed(Long userId, Pageable pageable) {
        User currentUser = userId != null ? userRepository.findById(userId).orElse(null) : null;

        if (currentUser != null) {
            List<Follower> following = followerRepository.findByFollower(currentUser);
            List<User> followedUsers = following.stream().map(Follower::getFollowing).toList();

            if (!followedUsers.isEmpty()) {
                Page<Post> followedPosts = postRepository.findByUsersOrderByCreatedAtDesc(followedUsers, pageable);
                if (!followedPosts.isEmpty()) {
                    return followedPosts.map(post -> mapToResponse(post, currentUser));
                }
            }
        }

        // Fallback to all active posts if no followed posts or unauthenticated
        return postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, pageable)
                .map(post -> mapToResponse(post, currentUser));
    }

    // ----------------- DISCOVERY FEED -----------------
    @Transactional(readOnly = true)
    public Page<PostResponse> getDiscoveryFeed(Long userId, Pageable pageable) {
        User currentUser = userId != null ? userRepository.findById(userId).orElse(null) : null;

        return postRepository.findAllByStatusOrderByCreatedAtDesc(PostStatus.ACTIVE, pageable)
                .map(post -> mapToResponse(post, currentUser));
    }

    // ----------------- ARTIST PROFILE POSTS -----------------
    @Transactional(readOnly = true)
    public Page<PostResponse> getArtistPosts(Long targetUserId, Long currentUserId, Pageable pageable) {
        User currentUser = currentUserId != null ? userRepository.findById(currentUserId).orElse(null) : null;
        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException("Target user not found", HttpStatus.NOT_FOUND));

        return postRepository.findByUserOrderByCreatedAtDesc(targetUser, pageable)
                .map(post -> mapToResponse(post, currentUser));
    }

    // ----------------- PAGINATED GET MY POSTS -----------------
    @Transactional(readOnly = true)
    public Page<PostResponse> getMyPosts(Long userId, Pageable pageable) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        return postRepository.findByUserOrderByCreatedAtDesc(currentUser, pageable)
                .map(post -> mapToResponse(post, currentUser));
    }

    // ----------------- SAVED POSTS -----------------
    @Transactional(readOnly = true)
    public Page<PostResponse> getSavedPosts(Long userId, Pageable pageable) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        return postRepository.findPostsSavedBy(currentUser, pageable)
                .map(post -> mapToResponse(post, currentUser));
    }

    // ----------------- SINGLE POST BY ID -----------------
    @Transactional(readOnly = true)
    public PostResponse getPostById(Long postId, Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        return mapToResponse(post, currentUser);
    }

    // ----------------- LIKE POST -----------------
    @Transactional
    public PostResponse likePost(Long postId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        boolean alreadyLiked = likeRepository.existsByUserAndLikeableTypeAndLikeableId(
                user, LikeableType.POST, postId
        );

        if (!alreadyLiked) {
            Like like = Like.builder()
                    .user(user)
                    .likeableType(LikeableType.POST)
                    .likeableId(postId)
                    .build();
            likeRepository.save(like);

            if (!post.getUser().getId().equals(userId)) {
                try {
                    notificationService.createNotification(
                            post.getUser(),
                            "New Like",
                            user.getFullName() + " liked your post.",
                            NotificationType.LIKE,
                            postId
                    );
                } catch (Exception e) {
                    log.error("Failed to create like notification: {}", e.getMessage());
                }
            }
        }

        return mapToResponse(post, user);
    }

    // ----------------- UNLIKE POST -----------------
    @Transactional
    public PostResponse unlikePost(Long postId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        likeRepository.findByUserAndLikeableTypeAndLikeableId(
                user, LikeableType.POST, postId
        ).ifPresent(likeRepository::delete);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        return mapToResponse(post, user);
    }

    // ----------------- REPOST POST -----------------
    @Transactional
    public PostResponse repostPost(Long postId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        if (!post.getRepostedBy().contains(user)) {
            post.getRepostedBy().add(user);
            postRepository.save(post);

            if (!post.getUser().getId().equals(userId)) {
                try {
                    notificationService.createNotification(
                            post.getUser(),
                            "New Repost",
                            user.getFullName() + " reposted your post.",
                            NotificationType.REPOST,
                            postId
                    );
                } catch (Exception e) {
                    log.error("Failed to create repost notification: {}", e.getMessage());
                }
            }
        }

        return mapToResponse(post, user);
    }

    // ----------------- UNREPOST POST -----------------
    @Transactional
    public PostResponse unrepostPost(Long postId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        post.getRepostedBy().remove(user);
        postRepository.save(post);

        return mapToResponse(post, user);
    }

    // ----------------- SAVE POST -----------------
    @Transactional
    public PostResponse savePost(Long postId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        if (!post.getSavedBy().contains(user)) {
            post.getSavedBy().add(user);
            postRepository.save(post);
        }

        return mapToResponse(post, user);
    }

    // ----------------- UNSAVE POST -----------------
    @Transactional
    public PostResponse unsavePost(Long postId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        post.getSavedBy().remove(user);
        postRepository.save(post);

        return mapToResponse(post, user);
    }

    // ----------------- COMMENTS FOR POST -----------------
    @Transactional(readOnly = true)
    public Page<CommentResponse> getPostComments(Long postId, Pageable pageable, Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        return commentRepository.findTopLevelCommentsByPost(post, pageable)
                .map(comment -> mapToCommentResponse(comment, currentUser));
    }

    // ----------------- ADD COMMENT -----------------
    @Transactional
    public CommentResponse addComment(Long postId, CommentRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        Comment parent = null;
        if (request.getParentId() != null) {
            parent = commentRepository.findById(request.getParentId())
                    .orElseThrow(() -> new AppException("Parent comment not found", HttpStatus.NOT_FOUND));
        }

        Comment comment = Comment.builder()
                .post(post)
                .user(user)
                .content(request.getContent())
                .parent(parent)
                .likedBy(new java.util.HashSet<>())
                .replies(new java.util.HashSet<>())
                .createdAt(LocalDateTime.now())
                .build();

        Comment savedComment = commentRepository.save(comment);

        // Notify post owner if commenter is not post author
        if (!post.getUser().getId().equals(userId)) {
            try {
                notificationService.createNotification(
                        post.getUser(),
                        "New Comment",
                        user.getFullName() + " commented: " + (comment.getContent().length() > 50 ? comment.getContent().substring(0, 50) + "..." : comment.getContent()),
                        NotificationType.COMMENT,
                        postId
                );
            } catch (Exception e) {
                log.error("Failed to create comment notification: {}", e.getMessage());
            }
        }

        // Tagged users (@username) notification processing
        if (request.getContent() != null && request.getContent().contains("@")) {
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("@([a-zA-Z0-9_.]+)");
            java.util.regex.Matcher matcher = pattern.matcher(request.getContent());
            java.util.Set<String> taggedUsernames = new java.util.HashSet<>();
            while (matcher.find()) {
                taggedUsernames.add(matcher.group(1).toLowerCase());
            }
            for (String username : taggedUsernames) {
                userRepository.findByUsername(username).ifPresent(taggedUser -> {
                    if (!taggedUser.getId().equals(userId)) {
                        try {
                            notificationService.createNotification(
                                    taggedUser,
                                    "Mentioned in Comment",
                                    user.getFullName() + " tagged you in a comment",
                                    NotificationType.COMMENT,
                                    postId
                            );
                        } catch (Exception e) {
                            log.error("Failed to create tag notification for {}: {}", username, e.getMessage());
                        }
                    }
                });
            }
        }

        return mapToCommentResponse(savedComment, user);
    }

    // ----------------- LIKE COMMENT -----------------
    @Transactional
    public CommentResponse likeComment(Long commentId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException("Comment not found", HttpStatus.NOT_FOUND));

        comment.addLike(user);
        commentRepository.save(comment);

        // Notify comment owner (not self)
        if (!comment.getUser().getId().equals(userId)) {
            try {
                notificationService.createNotification(
                        comment.getUser(),
                        "Comment Liked",
                        user.getFullName() + " liked your comment",
                        NotificationType.LIKE,
                        comment.getPost() != null ? comment.getPost().getId() : null
                );
            } catch (Exception e) {
                log.error("Failed to create comment like notification: {}", e.getMessage());
            }
        }
        return mapToCommentResponse(comment, user);
    }

    // ----------------- UNLIKE COMMENT -----------------
    @Transactional
    public CommentResponse unlikeComment(Long commentId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException("Comment not found", HttpStatus.NOT_FOUND));

        comment.removeLike(user);
        commentRepository.save(comment);
        return mapToCommentResponse(comment, user);
    }

    // ----------------- EDIT COMMENT -----------------
    @Transactional
    public CommentResponse editComment(Long commentId, CommentRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException("Comment not found", HttpStatus.NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new AppException("You are not authorized to edit this comment", HttpStatus.FORBIDDEN);
        }
        if (request.getContent() == null || request.getContent().isBlank()) {
            throw new AppException("Comment content cannot be empty", HttpStatus.BAD_REQUEST);
        }

        comment.setContent(request.getContent().trim());
        commentRepository.save(comment);
        return mapToCommentResponse(comment, user);
    }

    // ----------------- DELETE COMMENT -----------------
    @Transactional
    public void deleteComment(Long commentId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException("Comment not found", HttpStatus.NOT_FOUND));

        if (!comment.getUser().getId().equals(userId)) {
            throw new AppException("You are not authorized to delete this comment", HttpStatus.FORBIDDEN);
        }
        if (comment.getParent() != null && comment.getParent().getReplies() != null) {
            comment.getParent().getReplies().remove(comment);
        }
        commentRepository.delete(comment);
    }

    // ----------------- GET COMMENT REPLIES (PAGINATED) -----------------
    @Transactional(readOnly = true)
    public Page<CommentResponse> getCommentReplies(Long commentId, Pageable pageable, Long userId) {
        User currentUser = userId != null ? userRepository.findById(userId).orElse(null) : null;
        Comment parent = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException("Comment not found", HttpStatus.NOT_FOUND));

        return commentRepository.findByParent(parent, pageable)
                .map(reply -> mapToCommentResponse(reply, currentUser));
    }

    // ----------------- UPDATE POST -----------------
    @Transactional
    public PostResponse updatePost(Long postId, PostRequest request, Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new AppException("You are not authorized to update this post", HttpStatus.FORBIDDEN);
        }

        post.setDescription(request.getDescription());
        post.setVideoUrl(request.getVideoUrl());
        if (request.getImageUrls() != null) {
            post.setImageUrls(request.getImageUrls());
        }
        post.setAttachedType(request.getAttachedType());
        post.setAttachedTitle(request.getAttachedTitle());
        post.setAttachedSubtitle(request.getAttachedSubtitle());
        post.setAttachedPrice(request.getAttachedPrice());

        if (request.getProductId() != null) {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));
            post.setProduct(product);
        } else {
            post.setProduct(null);
        }

        post.setUpdatedAt(LocalDateTime.now());
        Post saved = postRepository.save(post);

        return mapToResponse(saved, currentUser);
    }

    // ----------------- DELETE POST -----------------
    @Transactional
    public void deletePost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new AppException("You are not authorized to delete this post", HttpStatus.FORBIDDEN);
        }

        likeRepository.deleteAllByLikeableTypeAndLikeableId(LikeableType.POST, postId);
        postRepository.delete(post);
    }

    // ----------------- MAPPERS -----------------
    private PostResponse mapToResponse(Post post, User currentUser) {
        long likesCount = likeRepository.countByLikeableTypeAndLikeableId(LikeableType.POST, post.getId());
        long commentsCount = commentRepository.countByPost(post);
        long repostsCount = post.getRepostedBy() != null ? post.getRepostedBy().size() : 0;
        long sharesCount = post.getSharedBy() != null ? post.getSharedBy().size() : 0;

        boolean isLiked = currentUser != null && likeRepository.existsByUserAndLikeableTypeAndLikeableId(currentUser, LikeableType.POST, post.getId());
        boolean isReposted = currentUser != null && post.getRepostedBy() != null && post.getRepostedBy().contains(currentUser);
        boolean isSaved = currentUser != null && post.getSavedBy() != null && post.getSavedBy().contains(currentUser);
        boolean userVerified = post.getUser().getRole() == Role.ARTIST || post.getUser().getRole() == Role.ADMIN;

        return PostResponse.builder()
                .id(post.getId())
                .userId(post.getUser().getId())
                .userName(post.getUser().getFullName())
                .userImageUrl(post.getUser().getProfilePicture())
                .userRole(post.getUser().getRole() != null ? post.getUser().getRole().name() : "USER")
                .userVerified(userVerified)
                .videoUrl(post.getVideoUrl())
                .imageUrls(post.getImageUrls() != null ? post.getImageUrls() : new ArrayList<>())
                .description(post.getDescription())
                .likesCount(likesCount)
                .commentsCount(commentsCount)
                .sharesCount(sharesCount)
                .repostsCount(repostsCount)
                .isLiked(isLiked)
                .isShared(false)
                .isReposted(isReposted)
                .isSaved(isSaved)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .product(post.getProduct() != null ? mapToProductResponse(post.getProduct()) : null)
                .isSponsored(post.isSponsored())
                .sponsorName(post.getSponsorName())
                .attachedType(post.getAttachedType())
                .attachedTitle(post.getAttachedTitle())
                .attachedSubtitle(post.getAttachedSubtitle())
                .attachedPrice(post.getAttachedPrice())
                .build();
    }

    private CommentResponse mapToCommentResponse(Comment comment, User currentUser) {
        List<CommentResponse> replies = (comment.getReplies() != null)
                ? comment.getReplies().stream().map(r -> mapToCommentResponse(r, currentUser)).toList()
                : new ArrayList<>();

        int likesCount = (comment.getLikedBy() != null) ? comment.getLikedBy().size() : 0;
        int repliesCount = (comment.getReplies() != null) ? comment.getReplies().size() : 0;
        boolean isLiked = (comment.getLikedBy() != null && currentUser != null) ? comment.getLikedBy().contains(currentUser) : false;

        return CommentResponse.builder()
                .id(comment.getId())
                .postId(comment.getPost() != null ? comment.getPost().getId() : null)
                .userId(comment.getUser() != null ? comment.getUser().getId() : null)
                .userName(comment.getUser() != null ? comment.getUser().getFullName() : "Anonymous")
                .userUsername(comment.getUser() != null ? comment.getUser().getUsername() : null)
                .userImageUrl(comment.getUser() != null ? comment.getUser().getProfilePicture() : null)
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .parentId(comment.getParent() != null ? comment.getParent().getId() : null)
                .isReply(comment.isReply())
                .likesCount(likesCount)
                .repliesCount(repliesCount)
                .isLiked(isLiked)
                .replies(replies)
                .build();
    }

    @Transactional(readOnly = true)
    public List<UserPublicDTO> getPostLikes(Long postId) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        if (post.getLikedBy() == null) {
            return new ArrayList<>();
        }

        return post.getLikedBy().stream()
                .map(user -> UserPublicDTO.builder()
                        .id(user.getId())
                        .fullName(user.getFullName())
                        .username(user.getUsername())
                        .email(user.getEmail())
                        .phoneNumber(user.getPhoneNumber())
                        .bio(user.getBio())
                        .profilePicture(user.getProfilePicture())
                        .role(user.getRole() != null ? user.getRole().name() : "USER")
                        .status(user.getStatus() != null ? user.getStatus().name() : "ACTIVE")
                        .accountVerified(user.isAccountVerified())
                        .fandomName(user.getFandomName())
                        .followersCount(followerRepository.countByFollowing(user))
                        .followingCount(followerRepository.countByFollower(user))
                        .createdAt(user.getAccountVerifiedAt() != null ? user.getAccountVerifiedAt() : java.time.LocalDateTime.of(2026, 1, 1, 0, 0))
                        .build())
                .toList();
    }

    private ProductResponse mapToProductResponse(Product product) {
        return ProductResponse.builder()
                .id(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .price(product.getPrice())
                .imageUrls(product.getImageUrls())
                .status(product.getStatus())
                .productType(product.getProductType())
                .build();
    }
}
