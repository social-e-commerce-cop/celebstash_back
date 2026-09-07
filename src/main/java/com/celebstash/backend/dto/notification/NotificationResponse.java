package com.celebstash.backend.dto.notification;

import com.celebstash.backend.model.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime createdAt;
    private Long relatedEntityId;
}
