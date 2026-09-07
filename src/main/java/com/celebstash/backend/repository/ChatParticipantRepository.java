package com.celebstash.backend.repository;

import com.celebstash.backend.model.ChatConversation;
import com.celebstash.backend.model.ChatParticipant;
import com.celebstash.backend.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatParticipantRepository extends JpaRepository<ChatParticipant, Long> {

    Optional<ChatParticipant> findByConversationAndUser(ChatConversation conversation, User user);

    boolean existsByConversationAndUser(ChatConversation conversation, User user);
}
