package com.celebstash.backend.service;

import com.celebstash.backend.exception.AppException;
import com.celebstash.backend.model.Notification;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.NotificationType;
import com.celebstash.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserService userService;

    @Transactional
    public Notification createNotification(User user, String title, String content, NotificationType type) {
        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .content(content)
                .type(type)
                .read(false)
                .createdAt(LocalDateTime.now())
                .build();
        return notificationRepository.save(notification);
    }

    @Transactional(readOnly = true)
    public List<Notification> getMyNotifications() {
        User currentUser = userService.getCurrentUser();
        return notificationRepository.findByUserOrderByCreatedAtDesc(currentUser);
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
}
