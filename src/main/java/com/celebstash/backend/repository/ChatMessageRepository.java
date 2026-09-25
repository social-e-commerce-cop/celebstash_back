package com.celebstash.backend.repository;

import com.celebstash.backend.model.ChatMessage;
import com.celebstash.backend.model.ChatConversation;
import com.celebstash.backend.model.enums.MessageType;
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

    /**
     * Messages in a conversation, excluding any the caller hid via "delete for me".
     * Filtering in the query (rather than after paging) keeps page sizes correct — dropping rows
     * from an already-fetched page would return short pages and break pagination.
     */
    @Query("""
           SELECT m FROM ChatMessage m
           WHERE m.conversation = :conv
             AND :userId NOT IN (SELECT h FROM ChatMessage m2 JOIN m2.hiddenFor h WHERE m2 = m)
           """)
    Page<ChatMessage> findVisibleForUser(@Param("conv") ChatConversation conversation,
                                         @Param("userId") Long userId,
                                         Pageable pageable);

    /**
     * Shared items in a conversation, newest first, for the media / docs / links / products tabs.
     *
     * <p>Deleted messages and anything the caller hid are excluded here rather than in the caller,
     * for the same paging reason as {@link #findVisibleForUser}. {@code requireMedia} is true for
     * the attachment tabs — an IMAGE row whose upload never produced a URL would otherwise render
     * as a broken tile — and false for product and post shares, which carry a reference instead
     * of a file.
     */
    @Query("""
           SELECT m FROM ChatMessage m
           WHERE m.conversation = :conv
             AND m.type IN :types
             AND m.isDeleted = false
             AND (:requireMedia = false OR m.mediaUrl IS NOT NULL)
             AND :userId NOT IN (SELECT h FROM ChatMessage m2 JOIN m2.hiddenFor h WHERE m2 = m)
           ORDER BY m.sentAt DESC
           """)
    Page<ChatMessage> findSharedForUser(@Param("conv") ChatConversation conversation,
                                        @Param("userId") Long userId,
                                        @Param("types") List<MessageType> types,
                                        @Param("requireMedia") boolean requireMedia,
                                        Pageable pageable);

    List<ChatMessage> findTop1ByConversationOrderBySentAtDesc(ChatConversation conversation);

    @Modifying
    @Query("UPDATE ChatMessage m SET m.readStatus = 'READ' WHERE m.conversation = :conv AND m.sender.id != :userId AND m.readStatus != 'READ'")
    int markAllAsRead(@Param("conv") ChatConversation conversation, @Param("userId") Long userId);
}
