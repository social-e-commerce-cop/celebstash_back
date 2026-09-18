package com.celebstash.backend.repository;

import com.celebstash.backend.model.ChatMessage;
import com.celebstash.backend.model.ChatConversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    Page<ChatMessage> findByConversationOrderBySentAtAsc(ChatConversation conversation, Pageable pageable);

    List<ChatMessage> findTop1ByConversationOrderBySentAtDesc(ChatConversation conversation);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.readStatus = 'READ' WHERE m.conversation = :conv AND m.sender.id != :userId AND m.readStatus != 'READ'")
    int markAllAsRead(@Param("conv") ChatConversation conversation, @Param("userId") Long userId);
}
