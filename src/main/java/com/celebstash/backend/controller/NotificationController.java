package com.celebstash.backend.controller;

import com.celebstash.backend.dto.notification.NotificationResponse;
import com.celebstash.backend.model.Notification;
import com.celebstash.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User activities alerts and notification log APIs")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieves the list of notifications for the logged-in user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        List<Notification> list = notificationService.getMyNotifications();
        List<NotificationResponse> responses = list.stream()
                .map(n -> NotificationResponse.builder()
                        .id(n.getId())
                        .title(n.getTitle())
                        .content(n.getContent())
                        .read(n.isRead())
                        .type(n.getType())
                        .createdAt(n.getCreatedAt())
                        .relatedEntityId(n.getRelatedEntityId())
                        .build())
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark notification as read", description = "Marks a specific notification as read by ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }
}
