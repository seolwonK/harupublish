package com.haru.common.security;

import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;

/**
 * Resolves an {@code Authorization: Bearer <jwt>} value into an authenticated
 * {@link HaruPrincipal}. Shared by the HTTP filter and the STOMP interceptor so
 * HTTP and WebSocket authentication cannot drift apart.
 */
@Component
public class BearerTokenResolver {

    public static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAccountRepository userAccountRepository;

    public BearerTokenResolver(JwtTokenProvider jwtTokenProvider, UserAccountRepository userAccountRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userAccountRepository = userAccountRepository;
    }

    /**
     * @throws io.jsonwebtoken.JwtException on an invalid or expired token
     * @throws com.haru.common.exception.HaruException when the account is not active
     * @throws IllegalArgumentException on a missing/malformed header or unknown user
     */
    public HaruPrincipal resolve(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new IllegalArgumentException("Authorization bearer token is required.");
        }
        Claims claims = jwtTokenProvider.parseClaims(authorizationHeader.substring(BEARER_PREFIX.length()));
        Long userId = Long.valueOf(claims.getSubject());
        UserAccount user = userAccountRepository.findWithRolesById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User was not found."));
        user.ensureActive();
        return new HaruPrincipal(
                user.getId(),
                user.getEmail(),
                user.getRoles(),
                user.getActiveRole()
        );
    }
}
