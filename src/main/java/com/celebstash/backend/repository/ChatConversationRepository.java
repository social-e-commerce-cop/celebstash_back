package com.celebstash.backend.repository;

import com.celebstash.backend.model.ChatConversation;
import com.celebstash.backend.model.User;
import com.celebstash.backend.model.enums.ConversationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatConversationRepository extends JpaRepository<ChatConversation, Long> {

    @Query("""
        SELECT DISTINCT c FROM ChatConversation c
        JOIN c.participants p
        WHERE p.user = :user
        ORDER BY c.updatedAt DESC
    """)
    List<ChatConversation> findAllByParticipant(@Param("user") User user);

    @Query("""
        SELECT c FROM ChatConversation c
        JOIN c.participants p1 ON p1.user = :user1
        JOIN c.participants p2 ON p2.user = :user2
        WHERE c.type = :type
        AND SIZE(c.participants) = 2
    """)
    Optional<ChatConversation> findDirectConversation(
        @Param("user1") User user1,
        @Param("user2") User user2,
        @Param("type") ConversationType type
    );
}
