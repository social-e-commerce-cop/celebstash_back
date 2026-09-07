    package com.celebstash.backend.model;

    import com.celebstash.backend.model.enums.PostStatus;
    import jakarta.persistence.*;
    import lombok.AllArgsConstructor;
    import lombok.Builder;
    import lombok.Data;
    import lombok.NoArgsConstructor;

    import java.time.LocalDateTime;
    import java.util.ArrayList;
    import java.util.List;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Entity
    @Table(name = "posts")
    public class Post {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(columnDefinition = "TEXT", nullable = true)
        private String description;

        @ElementCollection
        @CollectionTable(name = "post_images", joinColumns = @JoinColumn(name = "post_id"))
        @Column(name = "image_url", columnDefinition = "TEXT")
        private List<String> imageUrls = new ArrayList<>();

        @Column(columnDefinition = "TEXT", nullable = true)
        private String videoUrl;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "user_id", nullable = false)
        private User user;

        @ManyToOne(fetch = FetchType.LAZY)
        @JoinColumn(name = "product_id")
        private Product product;

        @Builder.Default
        private boolean isSponsored = false;

        private String sponsorName;

        private String attachedType;
        private String attachedTitle;
        private String attachedSubtitle;
        private String attachedPrice;

        @Enumerated(EnumType.STRING)
        @Column(nullable = false)
        private PostStatus status;

        @Column(nullable = false)
        private LocalDateTime createdAt;

        private LocalDateTime updatedAt;

        // Users who liked this post
        @ManyToMany
        @JoinTable(
                name = "post_likes",
                joinColumns = @JoinColumn(name = "post_id"),
                inverseJoinColumns = @JoinColumn(name = "user_id")
        )
        private List<User> likedBy = new ArrayList<>();

        // Users who shared this post
        @ManyToMany
        @JoinTable(
                name = "post_shares",
                joinColumns = @JoinColumn(name = "post_id"),
                inverseJoinColumns = @JoinColumn(name = "user_id")
        )
        private List<User> sharedBy = new ArrayList<>();

        // Users who reposted this post
        @ManyToMany
        @JoinTable(
                name = "post_reposts",
                joinColumns = @JoinColumn(name = "post_id"),
                inverseJoinColumns = @JoinColumn(name = "user_id")
        )
        private List<User> repostedBy = new ArrayList<>();

        // Users who saved this post
        @ManyToMany
        @JoinTable(
                name = "post_saves",
                joinColumns = @JoinColumn(name = "post_id"),
                inverseJoinColumns = @JoinColumn(name = "user_id")
        )
        private List<User> savedBy = new ArrayList<>();

        @PrePersist
        protected void onCreate() {
            createdAt = LocalDateTime.now();
            if (status == null) {
                status = PostStatus.ACTIVE;
            }
        }

        @PreUpdate
        protected void onUpdate() {
            updatedAt = LocalDateTime.now();
        }
    }
