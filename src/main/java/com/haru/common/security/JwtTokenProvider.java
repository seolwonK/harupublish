package com.haru.common.security;

import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
public class JwtTokenProvider {

    private final SecretKey secretKey;
    private final Duration accessTokenExpiration;

    public JwtTokenProvider(
            @Value("${haru.jwt.secret}") String secret,
            @Value("${haru.jwt.access-token-expiration-minutes:30}") long accessTokenExpirationMinutes
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiration = Duration.ofMinutes(accessTokenExpirationMinutes);
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
