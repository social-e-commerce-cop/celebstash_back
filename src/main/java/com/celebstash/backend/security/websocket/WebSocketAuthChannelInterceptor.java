package com.celebstash.backend.security.websocket;

import com.celebstash.backend.security.jwt.JwtUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.MessageHandler;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

import java.security.Principal;

/**
 * Authenticates STOMP WebSocket clients using the same JWT as the REST API.
 *
 * <p>The SockJS handshake at {@code /ws} is intentionally permitted without authentication — the
 * browser/native client cannot attach an Authorization header to the raw handshake. Authentication
 * therefore happens one layer up, on the STOMP CONNECT frame, which does carry headers. The
 * resolved user is attached to the session so every later frame on that connection is attributed
 * to it; a client cannot change identity mid-session.
 *
 * <p>Without this, {@code ChatService} (which resolves the sender through
 * {@code SecurityContextHolder}) has no authenticated user over WebSocket: sends fail, and the
 * endpoint is reachable anonymously.
 */
@Slf4j
@Component
public class WebSocketAuthChannelInterceptor implements ExecutorChannelInterceptor {

    private static final String AUTH_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtils jwtUtils;

    @Lazy
    @Autowired
    private UserDetailsService userDetailsService;

    public WebSocketAuthChannelInterceptor(JwtUtils jwtUtils) {
        this.jwtUtils = jwtUtils;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            Authentication authentication = authenticate(accessor);
            if (authentication == null) {
                // Reject the connection outright rather than allowing an anonymous session that
                // would later fail confusingly on the first send.
                throw new MessageDeliveryException("Unauthorized WebSocket connection: a valid Bearer token is required");
            }
            accessor.setUser(authentication);
        }

        return message;
    }

    /**
     * Establishes the security context on the thread that actually runs the handler.
     *
     * <p>{@code preSend} is not sufficient: the inbound channel is an executor-backed channel, so
     * the handler runs on a pooled thread while preSend ran on the caller's. A ThreadLocal set in
     * preSend is therefore invisible to the handler, and services resolving the user through
     * {@code SecurityContextHolder} fail with "Not authenticated". Implementing
     * {@link ExecutorChannelInterceptor} gives us a hook that runs on the handler's own thread.
     */
    @Override
    public Message<?> beforeHandle(Message<?> message, MessageChannel channel, MessageHandler handler) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && accessor.getUser() instanceof Authentication auth) {
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        return message;
    }

    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler, Exception ex) {
        // Executor threads are pooled and reused; leaving the context set would let the next
        // frame handled on this thread run as the previous user.
        SecurityContextHolder.clearContext();
    }

    private Authentication authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader(AUTH_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            log.debug("STOMP CONNECT rejected: missing or malformed Authorization header");
            return null;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            String username = jwtUtils.extractUsername(token);
            if (username == null) {
                return null;
            }

            UserDetails userDetails = userDetailsService.loadUserByUsername(username);

            // Same account-state rules as the HTTP filter: a locked or disabled account must not
            // hold an open socket just because its token has not expired yet.
            if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                log.debug("STOMP CONNECT rejected for inactive account: {}", username);
                return null;
            }

            if (!jwtUtils.validateToken(token, userDetails)) {
                log.debug("STOMP CONNECT rejected: invalid token for {}", username);
                return null;
            }

            return new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        } catch (Exception e) {
            log.debug("STOMP CONNECT rejected: {}", e.getMessage());
            return null;
        }
    }
}
