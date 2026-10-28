package com.celebstash.backend.service;

import com.celebstash.backend.dto.StoryRequest;
import com.celebstash.backend.dto.StoryResponse;
import com.celebstash.backend.dto.story.StoryViewerResponse;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.exception.ResourceNotFoundException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.model.enums.Role;
import com.celebstash.backend.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoryService {

    /** Stories are an artist feature: artists (and admins) publish, everyone else watches and reacts. */
    private static final Set<Role> STORY_AUTHOR_ROLES = Set.of(Role.ARTIST, Role.ADMIN);

    /** story_reactions.emoji is VARCHAR(10); one emoji can be several UTF-16 units (❤️ is 2). */
    private static final int MAX_EMOJI_LENGTH = 10;

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final StoryReactionRepository storyReactionRepository;
    private final StoryReplyRepository storyReplyRepository;
    private final StoryHighlightRepository storyHighlightRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public StoryResponse createStory(User user, StoryRequest request) {
        if (user.getRole() == null || !STORY_AUTHOR_ROLES.contains(user.getRole())) {
            throw new AppException("Only artists can post stories", HttpStatus.FORBIDDEN);
        }
        if (request.getMediaUrl() == null || request.getMediaUrl().isBlank()) {
            throw new AppException("A story needs a photo or video", HttpStatus.BAD_REQUEST);
        }

        // expiresAt is never taken from the client: Story.onCreate() sets it to createdAt + 24h.
        Story story = Story.builder()
                .user(user)
                .mediaUrl(request.getMediaUrl())
                .thumbnailUrl(request.getThumbnailUrl())
                .mediaType(request.getMediaType() != null ? request.getMediaType() : "IMAGE")
                .caption(request.getCaption())
                .visibility(request.getVisibility() != null ? request.getVisibility() : "PUBLIC")
                .allowReplies(request.isAllowReplies())
                .allowReactions(request.isAllowReactions())
                .viewCount(0)
                .reactionCount(0)
                .replyCount(0)
                .shareCount(0)
                .isArchived(false)
                .build();

        Story saved = storyRepository.save(story);
        return mapToResponse(saved, user);
    }

    /** Active (unexpired, unarchived) stories from artists, oldest first within each artist. */
    @Transactional(readOnly = true)
    public List<StoryResponse> getActiveFeedStories(User currentUser) {
        List<Story> activeStories = storyRepository.findActiveStoriesByAuthorRoles(
                LocalDateTime.now(), STORY_AUTHOR_ROLES);

        return activeStories.stream()
                .map(story -> mapToResponse(story, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> getUserActiveStories(Long userId, User currentUser) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        List<Story> stories = storyRepository.findActiveStoriesByUser(
                targetUser,
                LocalDateTime.now()
        );

        return stories.stream()
                .map(story -> mapToResponse(story, currentUser))
                .collect(Collectors.toList());
    }

    /**
     * Records that {@code viewer} watched the story. Each user counts once, the author's own
     * views are not counted, and the insert is race-safe (unique index on story_id + viewer_id).
     */
    @Transactional
    public void recordStoryView(
            Long storyId,
            User viewer,
            double watchDuration,
            boolean completed
    ) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        if (story.getUser().getId().equals(viewer.getId())) {
            return;
        }

        int inserted = storyViewRepository.insertIfAbsent(
                storyId, viewer.getId(), LocalDateTime.now(), watchDuration, completed);
        if (inserted > 0) {
            storyRepository.incrementViewCount(storyId);
        }
    }

    /**
     * Sets the user's reaction to the story. One reaction per user per story: reacting again
     * replaces the emoji rather than adding a second reaction.
     *
     * @return the user's reaction and the story's reaction count after the change
     */
    @Transactional
    public Map<String, Object> reactToStory(Long storyId, User user, String emoji) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        String cleanEmoji = emoji != null ? emoji.trim() : "";
        if (cleanEmoji.isEmpty() || cleanEmoji.length() > MAX_EMOJI_LENGTH) {
            throw new AppException("Invalid reaction", HttpStatus.BAD_REQUEST);
        }
        if (story.isExpired() || story.isArchived()) {
            throw new AppException("This story is no longer available", HttpStatus.GONE);
        }
        if (!story.isAllowReactions()) {
            throw new AppException("Reactions are turned off for this story", HttpStatus.FORBIDDEN);
        }
        if (story.getUser().getId().equals(user.getId())) {
            throw new AppException("You cannot react to your own story", HttpStatus.BAD_REQUEST);
        }

        Optional<StoryReaction> existing = storyReactionRepository.findByStoryAndUser(story, user);

        if (existing.isPresent()) {
            StoryReaction reaction = existing.get();
            reaction.setEmoji(cleanEmoji);
            storyReactionRepository.save(reaction);
        } else {
            StoryReaction reaction = StoryReaction.builder()
                    .story(story)
                    .user(user)
                    .emoji(cleanEmoji)
                    .build();

            storyReactionRepository.save(reaction);

            story.setReactionCount(story.getReactionCount() + 1);
            storyRepository.save(story);

            // Only the first reaction notifies; switching emoji does not ping the artist again.
            try {
                notificationService.createNotification(
                        story.getUser(),
                        user,
                        "Story Reaction",
                        user.getFullName() + " reacted " + cleanEmoji + " to your story",
                        NotificationType.STORY_INTERACTION,
                        story.getId());
            } catch (Exception e) {
                log.warn("Failed to notify story reaction {}: {}", storyId, e.getMessage());
            }
        }

        return Map.of(
                "message", "Reaction recorded",
                "myReaction", cleanEmoji,
                "reactionCount", story.getReactionCount());
    }

    /** Removes the user's reaction, if any. */
    @Transactional
    public Map<String, Object> removeReaction(Long storyId, User user) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        storyReactionRepository.findByStoryAndUser(story, user).ifPresent(reaction -> {
            storyReactionRepository.delete(reaction);
            story.setReactionCount(Math.max(0, story.getReactionCount() - 1));
            storyRepository.save(story);
        });

        return Map.of("message", "Reaction removed", "reactionCount", story.getReactionCount());
    }

    @Transactional
    public void replyToStory(Long storyId, User sender, String message) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        StoryReply reply = StoryReply.builder()
                .story(story)
                .sender(sender)
                .message(message)
                .build();

        storyReplyRepository.save(reply);

        story.setReplyCount(story.getReplyCount() + 1);
        storyRepository.save(story);
    }

    /** Who viewed the story, newest first, with any reaction. Only the author may see this. */
    @Transactional(readOnly = true)
    public List<StoryViewerResponse> getStoryViewers(Long storyId, User currentUser) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        if (!story.getUser().getId().equals(currentUser.getId())) {
            throw new AppException("Only the author can see who viewed this story", HttpStatus.FORBIDDEN);
        }

        Map<Long, String> reactionsByUser = storyReactionRepository.findByStoryOrderByCreatedAtDesc(story).stream()
                .collect(Collectors.toMap(r -> r.getUser().getId(), StoryReaction::getEmoji, (a, b) -> a));

        return storyViewRepository.findByStoryOrderByViewedAtDesc(story).stream()
                .map(view -> {
                    User viewer = view.getViewer();
                    return StoryViewerResponse.builder()
                            .userId(viewer.getId())
                            .fullName(viewer.getFullName())
                            .username(publicHandle(viewer))
                            .avatar(viewer.getProfilePicture())
                            .viewedAt(toInstant(view.getViewedAt()))
                            .reaction(reactionsByUser.get(viewer.getId()))
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteStory(Long storyId, User user) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        if (!story.getUser().getId().equals(user.getId())) {
            throw new AppException("You can only delete your own stories", HttpStatus.FORBIDDEN);
        }

        storyRepository.delete(story);
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> getUserArchivedStories(User user) {
        List<Story> archived =
                storyRepository.findArchivedStoriesByUser(user, LocalDateTime.now());

        return archived.stream()
                .map(story -> mapToResponse(story, user))
                .collect(Collectors.toList());
    }

    /** User.getUsername() falls back to email/phone, which must not be shown to other users. */
    private static String publicHandle(User u) {
        String handle = u.getUsername();
        if (handle == null || handle.equals(u.getEmail()) || handle.equals(u.getPhoneNumber())) return null;
        return handle;
    }

    /** Timestamps are written in UTC (the JVM zone is pinned in BackendApplication). */
    private static Instant toInstant(LocalDateTime value) {
        return value != null ? value.toInstant(ZoneOffset.UTC) : null;
    }

    private StoryResponse mapToResponse(Story story, User currentUser) {
        boolean isViewed =
                currentUser != null &&
                (story.getUser().getId().equals(currentUser.getId())
                        || storyViewRepository.existsByStoryAndViewer(story, currentUser));

        String myReaction = null;

        if (currentUser != null) {
            Optional<StoryReaction> reaction =
                    storyReactionRepository.findByStoryAndUser(story, currentUser);

            if (reaction.isPresent()) {
                myReaction = reaction.get().getEmoji();
            }
        }

        Function<User, String> role = u -> u.getRole() != null ? u.getRole().name() : "USER";

        return StoryResponse.builder()
                .id(story.getId())
                .userId(story.getUser().getId())
                .username(publicHandle(story.getUser()))
                .userFullName(story.getUser().getFullName())
                .userAvatar(story.getUser().getProfilePicture())
                .userRole(role.apply(story.getUser()))
                .mediaUrl(story.getMediaUrl())
                .thumbnailUrl(story.getThumbnailUrl())
                .mediaType(story.getMediaType())
                .caption(story.getCaption())
                .visibility(story.getVisibility())
                .productId(
                        story.getProduct() != null
                                ? story.getProduct().getId()
                                : null
                )
                .createdAt(toInstant(story.getCreatedAt()))
                .expiresAt(toInstant(story.getExpiresAt()))
                .viewCount(story.getViewCount())
                .reactionCount(story.getReactionCount())
                .replyCount(story.getReplyCount())
                .shareCount(story.getShareCount())
                .allowReplies(story.isAllowReplies())
                .allowReactions(story.isAllowReactions())
                .archived(story.isArchived() || story.isExpired())
                .viewedByMe(isViewed)
                .myReaction(myReaction)
                .build();
    }
}
