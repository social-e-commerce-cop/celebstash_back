package com.celebstash.backend.controller;

import com.celebstash.backend.dto.notification.NotificationResponse;
import com.celebstash.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User activities alerts and notification log APIs")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get user notifications", description = "Retrieves the most recent notifications (newest first) for the logged-in user")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<List<NotificationResponse>> getMyNotifications() {
        return ResponseEntity.ok(notificationService.getMyNotificationResponses());
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notification count", description = "Number of unread notifications, for the bell badge")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Long>> getUnreadCount() {
        return ResponseEntity.ok(Map.of("count", notificationService.getUnreadCount()));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark notification as read", description = "Marks a specific notification as read by ID")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read", description = "Explicit user action from the notifications screen")
    @SecurityRequirement(name = "bearerAuth")
    public ResponseEntity<Map<String, Integer>> markAllAsRead() {
        return ResponseEntity.ok(Map.of("updated", notificationService.markAllAsRead()));
    }
}
