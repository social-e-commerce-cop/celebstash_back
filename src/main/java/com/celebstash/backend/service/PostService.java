package com.celebstash.backend.service;

import com.celebstash.backend.dto.post.PostRequest;
import com.celebstash.backend.dto.post.PostResponse;
import com.celebstash.backend.dto.product.ProductResponse;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.LikeableType;
import com.celebstash.backend.model.enums.PostStatus;
import com.celebstash.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final LikeRepository likeRepository;

    @Transactional
    public PostResponse createPost(PostRequest request, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Product product = null;
        if (request.getProductId() != null) {
            product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));
        }

        Post post = new Post();
        post.setUser(user);
        post.setDescription(request.getDescription());
        post.setVideoUrl(request.getVideoUrl());
        post.setImageUrls(request.getImageUrls());
        post.setProduct(product);
        post.setStatus(PostStatus.ACTIVE);
        post.setCreatedAt(LocalDateTime.now());

        postRepository.save(post);
        return mapToResponse(post, user);
    }

    // ----------------- PAGINATED GET ALL POSTS -----------------
    @Transactional(readOnly = true)
    public Page<PostResponse> getAllPosts(Long userId, Pageable pageable) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        return postRepository.findAll(pageable)
                .map(post -> mapToResponse(post, currentUser));
    }

    // ----------------- PAGINATED GET MY POSTS -----------------
    @Transactional(readOnly = true)
    public Page<PostResponse> getMyPosts(Long userId, Pageable pageable) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        return postRepository.findByUser(currentUser, pageable)
                .map(post -> mapToResponse(post, currentUser));
    }

    @Transactional(readOnly = true)
    public PostResponse getPostById(Long postId, Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        return mapToResponse(post, currentUser);
    }

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
        }

        return mapToResponse(post, user);
    }

    @Transactional
    public PostResponse unlikePost(Long postId, Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Like like = likeRepository.findByUserAndLikeableTypeAndLikeableId(
                user, LikeableType.POST, postId
        ).orElseThrow(() -> new AppException("You haven't liked this post yet", HttpStatus.BAD_REQUEST));

        likeRepository.delete(like);

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        return mapToResponse(post, user);
    }


    @Transactional
    public PostResponse updatePost(Long postId, PostRequest request, Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException("User not found", HttpStatus.NOT_FOUND));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException("Post not found", HttpStatus.NOT_FOUND));

        if (!post.getUser().getId().equals(userId)) {
            throw new AppException("You are not authorized to update this post", HttpStatus.FORBIDDEN);
        }

        // Update post fields
        post.setDescription(request.getDescription());
        post.setVideoUrl(request.getVideoUrl());
        post.setImageUrls(request.getImageUrls());

        if (request.getProductId() != null) {
            Product product = productRepository.findById(request.getProductId())
                    .orElseThrow(() -> new AppException("Product not found", HttpStatus.NOT_FOUND));
            post.setProduct(product);
        } else {
            post.setProduct(null);
        }

        post.setUpdatedAt(LocalDateTime.now());
        postRepository.save(post);

        return mapToResponse(post, currentUser);
    }

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
        int likesCount = (int) likeRepository.countByLikeableTypeAndLikeableId(LikeableType.POST, post.getId());
        boolean isLiked = likeRepository.existsByUserAndLikeableTypeAndLikeableId(currentUser, LikeableType.POST, post.getId());

        return PostResponse.builder()
                .id(post.getId())
                .userId(post.getUser().getId())
                .userName(post.getUser().getFullName())
                .userImageUrl(post.getUser().getProfilePicture())
                .videoUrl(post.getVideoUrl())
                .imageUrls(post.getImageUrls())
                .description(post.getDescription())
                .likesCount(likesCount)
                .commentsCount(0)
                .sharesCount(0)
                .isLiked(isLiked)
                .isShared(false)
                .createdAt(post.getCreatedAt())
                .updatedAt(post.getUpdatedAt())
                .product(post.getProduct() != null ? mapToProductResponse(post.getProduct()) : null)
                .build();
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
