package com.haru.auth.application;

import com.haru.auth.api.dto.AuthTokenResponse;
import com.haru.auth.api.dto.LoginRequest;
import com.haru.auth.api.dto.LogoutRequest;
import com.haru.auth.api.dto.RefreshTokenRequest;
import com.haru.auth.api.dto.SignupRequest;
import com.haru.auth.domain.RefreshToken;
import com.haru.auth.infra.RefreshTokenRepository;
import com.haru.common.exception.BusinessException;
import com.haru.common.exception.ErrorCode;
import com.haru.common.exception.UnauthorizedException;
import com.haru.common.security.JwtTokenProvider;
import com.haru.tutor.domain.TutorProfileStatus;
import com.haru.tutor.infra.TutorProfileRepository;
import com.haru.user.api.dto.UserMeResponse;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final TokenHasher tokenHasher;
    private final TutorProfileRepository tutorProfileRepository;
    private final Duration refreshTokenExpiration;

    public AuthService(
            UserAccountRepository userAccountRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenGenerator refreshTokenGenerator,
            TokenHasher tokenHasher,
            TutorProfileRepository tutorProfileRepository,
            @Value("${haru.jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays
    ) {
        this.userAccountRepository = userAccountRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenGenerator = refreshTokenGenerator;
        this.tokenHasher = tokenHasher;
        this.tutorProfileRepository = tutorProfileRepository;
        this.refreshTokenExpiration = Duration.ofDays(refreshTokenExpirationDays);
    }

    @Transactional
    public AuthTokenResponse signup(SignupRequest request) {
        String email = request.email().toLowerCase();
        if (userAccountRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "Email already exists.");
        }

        UserAccount user = UserAccount.student(
                email,
                passwordEncoder.encode(request.password()),
                request.name(),
                request.timeZone()
        );
        userAccountRepository.save(user);
        return issueTokenPair(user);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest request) {
        UserAccount user = userAccountRepository.findByEmail(request.email().toLowerCase())
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password."));
        user.ensureActive();

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.");
        }

        user.recordLogin();
        return issueTokenPair(user);
    }

    @Transactional
    public AuthTokenResponse refresh(RefreshTokenRequest request) {
        RefreshToken currentToken = refreshTokenRepository.findByTokenHash(tokenHasher.sha256(request.refreshToken()))
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Refresh token is invalid."));

        if (currentToken.getRevokedAt() != null) {
            throw new UnauthorizedException(ErrorCode.REFRESH_TOKEN_REUSED, "Refresh token was already used or revoked.");
        }
        if (!currentToken.isUsable(Instant.now())) {
            throw new UnauthorizedException(ErrorCode.INVALID_TOKEN, "Refresh token is expired.");
        }

        UserAccount user = currentToken.getUser();
        user.ensureActive();

        String nextPlainToken = refreshTokenGenerator.generate();
        RefreshToken nextToken = refreshTokenRepository.save(newRefreshToken(user, nextPlainToken));
        currentToken.revoke(nextToken.getId());

        return new AuthTokenResponse(jwtTokenProvider.createAccessToken(user), nextPlainToken, userResponse(user));
    }

    @Transactional
    public void logout(LogoutRequest request) {
        refreshTokenRepository.findByTokenHash(tokenHasher.sha256(request.refreshToken()))
                .ifPresent(token -> {
                    if (token.getRevokedAt() == null) {
                        token.revoke(null);
                    }
                });
    }

    @Transactional(readOnly = true)
    public UserMeResponse me(Long userId) {
        UserAccount user = userAccountRepository.findWithRolesById(userId)
                .orElseThrow(() -> new UnauthorizedException("Authentication is required."));
        user.ensureActive();
        return userResponse(user);
    }

    private AuthTokenResponse issueTokenPair(UserAccount user) {
        String refreshToken = refreshTokenGenerator.generate();
        refreshTokenRepository.save(newRefreshToken(user, refreshToken));
        return new AuthTokenResponse(jwtTokenProvider.createAccessToken(user), refreshToken, userResponse(user));
    }

    private RefreshToken newRefreshToken(UserAccount user, String plainToken) {
        return RefreshToken.issue(
                user,
                tokenHasher.sha256(plainToken),
                Instant.now().plus(refreshTokenExpiration)
        );
    }

    private UserMeResponse userResponse(UserAccount user) {
        TutorProfileStatus tutorProfileStatus = tutorProfileRepository.findStatusByUserId(user.getId()).orElse(null);
        return UserMeResponse.from(user, tutorProfileStatus);
    }
}
