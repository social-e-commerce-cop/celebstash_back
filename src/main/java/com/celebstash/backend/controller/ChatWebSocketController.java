package com.celebstash.backend.controller;

import com.celebstash.backend.dto.chat.MessageDto;
import com.celebstash.backend.dto.chat.SendMessageRequest;
import com.celebstash.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * Handles real-time chat messages sent via WebSocket (STOMP).
 * Clients send to /app/chat/{conversationId}
 * Broadcast goes to /topic/conversation/{conversationId}
 */
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;

    @MessageMapping("/chat/{conversationId}")
    public void handleMessage(
            @DestinationVariable Long conversationId,
            @Payload SendMessageRequest req) {

        // Persist via service
        MessageDto saved = chatService.sendMessage(conversationId, req);

        // Broadcast to all subscribers of this conversation
        messagingTemplate.convertAndSend(
                "/topic/conversation/" + conversationId, saved);
    }
}
