package com.haru.chat.config;

import com.haru.chat.infra.ChatRoomParticipantRepository;
import com.haru.common.exception.HaruException;
import com.haru.common.security.HaruPrincipal;
import com.haru.common.security.JwtTokenProvider;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * JWT authentication and authorization for the STOMP channel.
 *
 * <ul>
 *   <li>CONNECT — validates the {@code Authorization} native header and binds a
 *       {@link StompPrincipal} to the session.</li>
 *   <li>SUBSCRIBE — room topics require membership; user queues are always
 *       scoped to the session principal by the broker.</li>
 *   <li>SEND — rejected entirely: messages enter through the REST API only, so
 *       clients cannot publish straight into the SimpleBroker.</li>
 * </ul>
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAccountRepository userAccountRepository;
    private final ChatRoomParticipantRepository participantRepository;

    public StompAuthChannelInterceptor(
            JwtTokenProvider jwtTokenProvider,
            UserAccountRepository userAccountRepository,
            ChatRoomParticipantRepository participantRepository
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userAccountRepository = userAccountRepository;
        this.participantRepository = participantRepository;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscription(accessor);
            case SEND -> throw new MessagingException("Sending messages over STOMP is not supported. Use the REST API.");
            default -> {
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            throw new MessagingException("Authorization bearer token is required.");
        }
        try {
            Claims claims = jwtTokenProvider.parseClaims(authorization.substring(BEARER_PREFIX.length()));
            Long userId = Long.valueOf(claims.getSubject());
            UserAccount user = userAccountRepository.findWithRolesById(userId)
                    .orElseThrow(() -> new MessagingException("User was not found."));
            user.ensureActive();
            HaruPrincipal principal = new HaruPrincipal(
                    user.getId(),
                    user.getEmail(),
                    user.getRoles(),
                    user.getActiveRole()
            );
            accessor.setUser(new StompPrincipal(principal));
        } catch (JwtException | IllegalArgumentException | HaruException exception) {
            throw new MessagingException("Invalid access token.");
        }
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof StompPrincipal principal)) {
            throw new MessagingException("Authentication is required to subscribe.");
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("Subscription destination is required.");
        }
        if (destination.startsWith("/user/")) {
            return;
        }
        if (destination.startsWith(WebSocketConfig.ROOM_TOPIC_PREFIX)) {
            Long roomId = parseRoomId(destination);
            if (!participantRepository.existsByChatRoomIdAndUserId(roomId, principal.userId())) {
                throw new MessagingException("You do not have access to this chat room.");
            }
            return;
        }
        throw new MessagingException("Subscription to this destination is not allowed.");
    }

    private Long parseRoomId(String destination) {
        try {
            return Long.valueOf(destination.substring(WebSocketConfig.ROOM_TOPIC_PREFIX.length()));
        } catch (NumberFormatException exception) {
            throw new MessagingException("Invalid chat room destination.");
        }
    }
}
