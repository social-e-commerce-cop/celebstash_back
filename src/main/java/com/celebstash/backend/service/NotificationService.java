package com.celebstash.backend.service;

import com.celebstash.backend.dto.notification.NotificationResponse;
import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Notification;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.repository.NotificationRepository;
import com.celebstash.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    /** STOMP destination a signed-in client subscribes to as /user/queue/notifications. */
    public static final String USER_QUEUE = "/queue/notifications";

    /** How many notifications the list endpoint returns. */
    private static final int LIST_LIMIT = 100;

    /**
     * Actions a user can repeat on the same target (like → unlike → like, follow → unfollow →
     * follow). Within this window the recipient is told once rather than once per toggle.
     */
    private static final Set<NotificationType> DEDUPED_TYPES = EnumSet.of(
            NotificationType.LIKE,
            NotificationType.LIKE_COMMENT_ACTIVITY,
            NotificationType.NEW_FOLLOWER,
            NotificationType.REPOST,
            NotificationType.STORY_INTERACTION);
    private static final long DEDUPE_WINDOW_HOURS = 24;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification createNotification(User user, String title, String content, NotificationType type) {
        return createNotification(user, null, title, content, type, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification createNotification(User user, String title, String content, NotificationType type, Long relatedEntityId) {
        return createNotification(user, null, title, content, type, relatedEntityId);
    }

    /**
     * Records a notification caused by {@code actor} and pushes it to the recipient's open app.
     *
     * <ul>
     *   <li>Nobody is notified about their own action.</li>
     *   <li>Repeated toggles (see {@link #DEDUPED_TYPES}) inside the window produce one notification.</li>
     *   <li>Chat messages collapse into one unread notification per sender and conversation, which
     *       is refreshed with the latest message instead of stacking a row per message.</li>
     * </ul>
     *
     * Runs in its own transaction so a notification failure never rolls back the action itself.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification createNotification(User user, User actor, String title, String content,
                                           NotificationType type, Long relatedEntityId) {
        try {
            // Re-load by id: callers often pass lazy proxies from another (possibly closed)
            // persistence context, and the push below needs the recipient's username.
            User recipient = userRepository.findById(user.getId()).orElse(null);
            if (recipient == null) return null;
            User actorUser = actor != null ? userRepository.findById(actor.getId()).orElse(null) : null;
            if (actorUser != null && actorUser.getId().equals(recipient.getId())) return null;

            LocalDateTime now = LocalDateTime.now();
            Optional<Notification> previous = (actorUser != null && relatedEntityId != null)
                    ? notificationRepository.findFirstByUserAndActorAndTypeAndRelatedEntityIdOrderByCreatedAtDesc(
                            recipient, actorUser, type, relatedEntityId)
                    : Optional.empty();

            Notification notification;
            if (previous.isPresent() && DEDUPED_TYPES.contains(type)
                    && previous.get().getCreatedAt().isAfter(now.minusHours(DEDUPE_WINDOW_HOURS))) {
                return previous.get();
            } else if (previous.isPresent() && type == NotificationType.NEW_MESSAGE && !previous.get().isRead()) {
                notification = previous.get();
                notification.setTitle(title);
                notification.setContent(content);
                notification.setCreatedAt(now);
            } else {
                notification = Notification.builder()
                        .user(recipient)
                        .actor(actorUser)
                        .title(title)
                        .content(content)
                        .type(type)
                        .relatedEntityId(relatedEntityId)
                        .read(false)
                        .createdAt(now)
                        .build();
            }

            Notification saved = notificationRepository.save(notification);
            pushAfterCommit(recipient.getUsername(), toResponse(saved));
            return saved;
        } catch (Exception e) {
            log.error("Failed to create in-app notification: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Sends the notification to the recipient's live connection once the row is committed, so a
     * client that reacts by refetching never misses it. Delivery is best-effort: a user who is
     * offline simply sees it in the list next time.
     */
    private void pushAfterCommit(String principalName, NotificationResponse payload) {
        Runnable send = () -> {
            try {
                messagingTemplate.convertAndSendToUser(principalName, USER_QUEUE, payload);
            } catch (Exception e) {
                log.warn("Failed to push notification {}: {}", payload.getId(), e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    @Transactional(readOnly = true)
    public List<Notification> getMyNotifications() {
        User currentUser = userService.getCurrentUser();
        return notificationRepository.findRecentWithActor(currentUser, PageRequest.of(0, LIST_LIMIT));
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getMyNotificationResponses() {
        return getMyNotifications().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount() {
        return notificationRepository.countByUserAndReadFalse(userService.getCurrentUser());
    }

    @Transactional
    public void markAsRead(Long notificationId) {
        User currentUser = userService.getCurrentUser();
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new AppException("Notification not found", HttpStatus.NOT_FOUND));

        if (!notification.getUser().getId().equals(currentUser.getId())) {
            throw new AppException("You can only update your own notifications", HttpStatus.FORBIDDEN);
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    @Transactional
    public int markAllAsRead() {
        return notificationRepository.markAllRead(userService.getCurrentUser());
    }

    /**
     * Clears notifications about a target the user has now seen directly — e.g. opening a
     * conversation reads its message notifications, so the bell badge does not keep counting them.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markReadByTarget(User user, NotificationType type, Long relatedEntityId) {
        try {
            notificationRepository.markReadByTarget(user, type, relatedEntityId);
        } catch (Exception e) {
            log.warn("Failed to mark {} notifications read for {}: {}", type, relatedEntityId, e.getMessage());
        }
    }

    /**
     * User.getUsername() falls back to the email or phone number for accounts without a handle;
     * that is a login identifier, not something to show other users.
     */
    private static String publicHandle(User u) {
        if (u == null) return null;
        String handle = u.getUsername();
        if (handle == null || handle.equals(u.getEmail()) || handle.equals(u.getPhoneNumber())) return null;
        return handle;
    }

    public NotificationResponse toResponse(Notification n) {
        User actor = n.getActor();
        return NotificationResponse.builder()
                .id(n.getId())
                .title(n.getTitle())
                .content(n.getContent())
                .read(n.isRead())
                .type(n.getType())
                .createdAt(n.getCreatedAt() != null ? n.getCreatedAt().toInstant(ZoneOffset.UTC) : null)
                .relatedEntityId(n.getRelatedEntityId())
                .actorId(actor != null ? actor.getId() : null)
                .actorName(actor != null ? actor.getFullName() : null)
                .actorUsername(publicHandle(actor))
                .actorAvatar(actor != null ? actor.getProfilePicture() : null)
                .build();
    }
}
