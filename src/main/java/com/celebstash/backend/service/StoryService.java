package com.celebstash.backend.service;

import com.celebstash.backend.dto.StoryRequest;
import com.celebstash.backend.dto.StoryResponse;
import com.celebstash.backend.exception.ResourceNotFoundException;
import com.celebstash.backend.model.*;
import com.celebstash.backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final StoryReactionRepository storyReactionRepository;
    private final StoryReplyRepository storyReplyRepository;
    private final StoryHighlightRepository storyHighlightRepository;
    private final UserRepository userRepository;

    @Transactional
    public StoryResponse createStory(User user, StoryRequest request) {
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

    @Transactional(readOnly = true)
    public List<StoryResponse> getActiveFeedStories(User currentUser) {
        LocalDateTime now = LocalDateTime.now();
        List<Story> activeStories = storyRepository.findAllActiveStories(now);

        return activeStories.stream()
                .map(story -> mapToResponse(story, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> getUserActiveStories(Long userId, User currentUser) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
        
        List<Story> stories = storyRepository.findActiveStoriesByUser(targetUser, LocalDateTime.now());
        return stories.stream()
                .map(story -> mapToResponse(story, currentUser))
                .collect(Collectors.toList());
    }

    @Transactional
    public void recordStoryView(Long storyId, User viewer, double watchDuration, boolean completed) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        Optional<StoryView> existing = storyViewRepository.findByStoryAndViewer(story, viewer);
        if (existing.isEmpty()) {
            StoryView view = StoryView.builder()
                    .story(story)
                    .viewer(viewer)
                    .watchDuration(watchDuration)
                    .completed(completed)
                    .build();
            storyViewRepository.save(view);
            story.setViewCount(story.getViewCount() + 1);
            storyRepository.save(story);
        }
    }

    @Transactional
    public void reactToStory(Long storyId, User user, String emoji) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        Optional<StoryReaction> existing = storyReactionRepository.findByStoryAndUser(story, user);
        if (existing.isPresent()) {
            StoryReaction reaction = existing.get();
            reaction.setEmoji(emoji);
            storyReactionRepository.save(reaction);
        } else {
            StoryReaction reaction = StoryReaction.builder()
                    .story(story)
                    .user(user)
                    .emoji(emoji)
                    .build();
            storyReactionRepository.save(reaction);
            story.setReactionCount(story.getReactionCount() + 1);
            storyRepository.save(story);
        }
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

    @Transactional
    public void deleteStory(Long storyId, User user) {
        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new ResourceNotFoundException("Story not found: " + storyId));

        if (!story.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized deletion request");
        }
        storyRepository.delete(story);
    }

    @Transactional(readOnly = true)
    public List<StoryResponse> getUserArchivedStories(User user) {
        List<Story> archived = storyRepository.findArchivedStoriesByUser(user, LocalDateTime.now());
        return archived.stream()
                .map(story -> mapToResponse(story, user))
                .collect(Collectors.toList());
    }

    private StoryResponse mapToResponse(Story story, User currentUser) {
        boolean isViewed = currentUser != null && storyViewRepository.existsByStoryAndViewer(story, currentUser);
        String myReaction = null;
        if (currentUser != null) {
            Optional<StoryReaction> reaction = storyReactionRepository.findByStoryAndUser(story, currentUser);
            if (reaction.isPresent()) {
                myReaction = reaction.get().getEmoji();
            }
        }

        return StoryResponse.builder()
                .id(story.getId())
                .userId(story.getUser().getId())
                .username(story.getUser().getUsername())
                .userFullName(story.getUser().getFullName())
                .userAvatar(story.getUser().getProfilePicture())
                .userRole(story.getUser().getRole() != null ? story.getUser().getRole().name() : "USER")
                .mediaUrl(story.getMediaUrl())
                .thumbnailUrl(story.getThumbnailUrl())
                .mediaType(story.getMediaType())
                .caption(story.getCaption())
                .visibility(story.getVisibility())
                .productId(story.getProduct() != null ? story.getProduct().getId() : null)
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .viewCount(story.getViewCount())
                .reactionCount(story.getReactionCount())
                .replyCount(story.getReplyCount())
                .shareCount(story.getShareCount())
                .allowReplies(story.isAllowReplies())
                .allowReactions(story.isAllowReactions())
                .isArchived(story.isArchived() || story.isExpired())
                .isViewedByMe(isViewed)
                .myReaction(myReaction)
                .build();
    }
}
