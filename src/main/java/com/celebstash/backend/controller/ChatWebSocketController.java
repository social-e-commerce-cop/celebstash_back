package com.celebstash.backend.controller;

import com.celebstash.backend.dto.chat.ChatEvent;
import com.celebstash.backend.dto.chat.MessageDto;
import com.celebstash.backend.dto.chat.SendMessageRequest;
import com.celebstash.backend.model.User;
import com.celebstash.backend.service.ChatService;
import com.celebstash.backend.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * Real-time chat entry points (STOMP).
 *
 * <p>Clients send to {@code /app/chat/...}; broadcasts go to {@code /topic/conversation/{id}}
 * (bare message, legacy) and {@code /topic/conversation/{id}/events} (typed envelope).
 *
 * <p>The authenticated principal comes from the CONNECT frame via
 * {@code WebSocketAuthChannelInterceptor} — never from the payload. A client cannot claim to be
 * another user by putting a senderId in the body, because the body has no such field and the
 * service resolves the sender from the security context.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatService chatService;
    private final PresenceService presenceService;

    /**
     * Send a message over the socket. Persistence, membership checks and broadcasting all happen
     * inside ChatService, so REST and WebSocket sends behave identically.
     */
    @MessageMapping("/chat/{conversationId}")
    public void handleMessage(
            @DestinationVariable Long conversationId,
            @Payload SendMessageRequest req) {
        MessageDto saved = chatService.sendMessage(conversationId, req);
        log.debug("WS message {} persisted in conversation {}", saved.id(), conversationId);
    }

    /**
     * Typing start/stop. Transient — broadcast only, never persisted.
     *
     * <p>The payload is read as a raw String rather than a Boolean: STOMP frames from the mobile
     * client arrive without a JSON content-type, so a bare {@code true} does not reliably convert
     * to a Boolean and the frame is dropped before reaching the handler.
     */
    @MessageMapping("/chat/{conversationId}/typing")
    public void handleTyping(
            @DestinationVariable Long conversationId,
            @Payload(required = false) String body,
            Principal principal) {
        User me = resolve(principal);
        if (me == null) return;
        // Anything other than an explicit "false" counts as "still typing".
        boolean typing = body == null || body.isBlank() || !body.trim().toLowerCase().contains("false");
        chatService.broadcastTyping(conversationId, me, typing);
    }

    /**
     * Keeps the sender's online window open. Clients call this on connect and periodically;
     * missing heartbeats age the user out rather than leaving them online forever.
     */
    @MessageMapping("/presence/heartbeat")
    public void heartbeat(Principal principal) {
        User me = resolve(principal);
        if (me == null) return;
        presenceService.heartbeat(me.getId());
        chatService.broadcastPresence(me, true);
    }

    private User resolve(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }
}
