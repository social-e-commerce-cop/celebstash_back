package com.celebstash.backend.dto.notification;

import com.celebstash.backend.model.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String title;
    private String content;
    private boolean read;
    private NotificationType type;
    /**
     * An absolute instant, serialised with a trailing "Z" (UTC). A zoneless LocalDateTime here
     * was parsed by phones as their own local time, shifting every notification by the device's
     * UTC offset.
     */
    private Instant createdAt;
    /**
     * What the notification is about: the post for LIKE/COMMENT/REPOST, the conversation for
     * NEW_MESSAGE, the follower for NEW_FOLLOWER, the story for STORY_INTERACTION.
     */
    private Long relatedEntityId;

    private Long actorId;
    private String actorName;
    private String actorUsername;
    private String actorAvatar;
}
