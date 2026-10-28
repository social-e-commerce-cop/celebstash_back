package com.celebstash.backend.repository;

import com.celebstash.backend.model.Notification;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserOrderByCreatedAtDesc(User user);

    /** The newest notifications with their actor loaded in the same query. */
    @Query("SELECT n FROM Notification n LEFT JOIN FETCH n.actor WHERE n.user = :user ORDER BY n.createdAt DESC, n.id DESC")
    List<Notification> findRecentWithActor(@Param("user") User user, org.springframework.data.domain.Pageable pageable);

    long countByUserAndReadFalse(User user);

    /** Most recent notification for the same actor/action/target — used to collapse repeats. */
    Optional<Notification> findFirstByUserAndActorAndTypeAndRelatedEntityIdOrderByCreatedAtDesc(
            User user, User actor, NotificationType type, Long relatedEntityId);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user = :user AND n.read = false")
    int markAllRead(@Param("user") User user);

    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user = :user AND n.read = false AND n.type = :type AND n.relatedEntityId = :relatedEntityId")
    int markReadByTarget(@Param("user") User user, @Param("type") NotificationType type, @Param("relatedEntityId") Long relatedEntityId);
}
