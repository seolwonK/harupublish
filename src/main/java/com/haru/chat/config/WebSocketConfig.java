package com.haru.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haru.common.security.SecurityConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.converter.DefaultContentTypeResolver;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * STOMP over WebSocket with the in-memory SimpleBroker. Clients only SUBSCRIBE
 * here — messages are sent through the REST API and published to the broker by
 * {@link com.haru.chat.application.ChatBroadcaster}. Single-instance deployment
 * is assumed; scaling out requires an external broker relay.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    public static final String STOMP_ENDPOINT = "/ws-chat";
    public static final String ROOM_TOPIC_PREFIX = "/topic/chat-rooms/";
    public static final String USER_UNREAD_QUEUE = "/queue/unread";

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final ObjectMapper objectMapper;
    private final List<String> allowedOriginPatterns;

    public WebSocketConfig(
            StompAuthChannelInterceptor stompAuthChannelInterceptor,
            ObjectMapper objectMapper,
            @Value("${haru.cors.allowed-origin-patterns}") String corsAllowedOriginPatterns
    ) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.objectMapper = objectMapper;
        this.allowedOriginPatterns = SecurityConfig.resolveCorsPatterns(corsAllowedOriginPatterns);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(STOMP_ENDPOINT)
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    /** Use the Boot-managed ObjectMapper so Instant fields serialize as ISO-8601. */
    @Override
    public boolean configureMessageConverters(List<MessageConverter> messageConverters) {
        DefaultContentTypeResolver resolver = new DefaultContentTypeResolver();
        resolver.setDefaultMimeType(MimeTypeUtils.APPLICATION_JSON);
        MappingJackson2MessageConverter converter = new MappingJackson2MessageConverter();
        converter.setObjectMapper(objectMapper);
        converter.setContentTypeResolver(resolver);
        messageConverters.add(converter);
        return false;
    }
}
