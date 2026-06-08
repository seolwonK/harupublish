package com.haru.common.security;

import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Component
public class JwtTokenProvider {

    /**
     * The development-only placeholder shipped as the default in application.yml.
     * If this exact value is still in effect outside a local/test profile the app
     * fails fast on boot (defect #7) instead of signing real tokens with a public,
     * checked-in secret.
     */
    static final String DEFAULT_PLACEHOLDER_SECRET =
            "change-this-local-development-secret-key-minimum-32-bytes";

    private static final Set<String> SAFE_PLACEHOLDER_PROFILES = Set.of("local", "test", "dev");

    private final SecretKey secretKey;
    private final Duration accessTokenExpiration;

    public JwtTokenProvider(
            @Value("${haru.jwt.secret}") String secret,
            @Value("${haru.jwt.access-token-expiration-minutes:30}") long accessTokenExpirationMinutes,
            Environment environment
    ) {
        validateSecretForProfile(secret, environment);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = Duration.ofMinutes(accessTokenExpirationMinutes);
    }

    /**
     * Fail fast (defect #7): refuse to boot in any non-local/test profile while the
     * JWT secret is still the checked-in development placeholder. With no active
     * profile (production default) the placeholder is also rejected, so a real
     * {@code HARU_JWT_SECRET} must be supplied before the app will start.
     */
    private static void validateSecretForProfile(String secret, Environment environment) {
        if (!DEFAULT_PLACEHOLDER_SECRET.equals(secret)) {
            return;
        }
        String[] activeProfiles = environment.getActiveProfiles();
        boolean safeProfile = activeProfiles.length > 0
                && List.of(activeProfiles).stream().allMatch(SAFE_PLACEHOLDER_PROFILES::contains);
        if (!safeProfile) {
            throw new IllegalStateException(
                    "haru.jwt.secret is still the development placeholder. Set HARU_JWT_SECRET "
                            + "to a real 32+ byte secret for non-local/test profiles before starting the app.");
        }
    }

    public String createAccessToken(UserAccount user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream()
                .map(Role::name)
                .sorted()
                .toList();

        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .claim("activeRole", user.getActiveRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTokenExpiration)))
                .signWith(secretKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
