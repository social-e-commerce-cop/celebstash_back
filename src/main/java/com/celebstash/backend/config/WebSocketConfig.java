package com.celebstash.backend.config;

import com.celebstash.backend.security.websocket.WebSocketAuthChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthChannelInterceptor authChannelInterceptor;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Client subscribes to /topic/... to receive messages
        registry.enableSimpleBroker("/topic", "/queue");
        // Client sends to /app/... to send messages via WebSocket
        registry.setApplicationDestinationPrefixes("/app");
        // Enables per-user destinations (convertAndSendToUser -> /user/queue/...)
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Authenticates the STOMP CONNECT frame and re-establishes the security context for every
     * inbound frame, so services resolve the current user over WebSocket exactly as over REST.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(authChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // SockJS fallback endpoint — React Native connects here
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
