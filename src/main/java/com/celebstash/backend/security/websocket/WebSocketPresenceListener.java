package com.celebstash.backend.security.websocket;

import com.celebstash.backend.model.User;
import com.celebstash.backend.service.ChatService;
import com.celebstash.backend.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flips presence on when a socket connects and off when it drops.
 *
 * <p>Relying on an explicit "I'm leaving" message would leave users stuck online after a crash or
 * a tunnel dying, so offline is driven by the broker's own disconnect event. The heartbeat TTL in
 * {@link PresenceService} is the backstop for the case where even the disconnect event is missed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceListener {

    private static final Pattern CONVERSATION_DESTINATION =
            Pattern.compile("/topic/conversation/(\\d+)");

    private final PresenceService presenceService;
    private final ChatService chatService;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        User user = resolve(StompHeaderAccessor.wrap(event.getMessage()).getUser());
        if (user == null) return;
        presenceService.heartbeat(user.getId());
        safeBroadcast(user, true);
        log.debug("Presence: {} online", user.getId());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        User user = resolve(StompHeaderAccessor.wrap(event.getMessage()).getUser());
        if (user == null) return;
        presenceService.markOffline(user.getId());
        presenceService.clearActiveConversation(user.getId());
        safeBroadcast(user, false);
        log.debug("Presence: {} offline", user.getId());
    }

    /**
     * Subscribing to a conversation's event topic means the user has that thread open, which
     * suppresses notifications for it. Derived from the subscription rather than a client-sent
     * "I'm viewing X" flag, so it cannot be spoofed and cleans itself up on disconnect.
     */
    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        User user = resolve(accessor.getUser());
        Long convId = conversationIdFrom(accessor.getDestination());
        if (user != null && convId != null) {
            presenceService.setActiveConversation(user.getId(), convId);
        }
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        User user = resolve(StompHeaderAccessor.wrap(event.getMessage()).getUser());
        if (user != null) {
            presenceService.clearActiveConversation(user.getId());
        }
    }

    /** Extracts the id from /topic/conversation/{id}[/events]. */
    private Long conversationIdFrom(String destination) {
        if (destination == null) return null;
        Matcher m = CONVERSATION_DESTINATION.matcher(destination);
        if (!m.find()) return null;
        try {
            return Long.valueOf(m.group(1));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Presence is best-effort: a failure here must not disturb connection handling. */
    private void safeBroadcast(User user, boolean online) {
        try {
            chatService.broadcastPresence(user, online);
        } catch (Exception e) {
            log.debug("Presence broadcast skipped for {}: {}", user.getId(), e.getMessage());
        }
    }

    private User resolve(Principal principal) {
        if (principal instanceof Authentication auth && auth.getPrincipal() instanceof User user) {
            return user;
        }
        return null;
    }
}
