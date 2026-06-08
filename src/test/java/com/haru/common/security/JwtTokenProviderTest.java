package com.haru.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Defect #7: the JWT secret placeholder must never be used outside a local/test/dev
 * profile. These are plain unit tests (no Spring context) so the boot-time guard is
 * exercised directly.
 */
class JwtTokenProviderTest {

    private static final String REAL_SECRET = "a-real-production-secret-key-with-32+bytes-yes";

    @Test
    void failsFastWhenPlaceholderSecretUsedWithNoActiveProfile() {
        // No active profile = production default. Placeholder must be rejected.
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> new JwtTokenProvider(
                JwtTokenProvider.DEFAULT_PLACEHOLDER_SECRET, 30, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HARU_JWT_SECRET");
    }

    @Test
    void failsFastWhenPlaceholderSecretUsedWithProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");

        assertThatThrownBy(() -> new JwtTokenProvider(
                JwtTokenProvider.DEFAULT_PLACEHOLDER_SECRET, 30, environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void failsFastWhenPlaceholderMixedWithNonSafeProfile() {
        // Any non-safe profile in the active set must reject the placeholder.
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local", "prod");

        assertThatThrownBy(() -> new JwtTokenProvider(
                JwtTokenProvider.DEFAULT_PLACEHOLDER_SECRET, 30, environment))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void allowsPlaceholderSecretInLocalProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        assertThatCode(() -> new JwtTokenProvider(
                JwtTokenProvider.DEFAULT_PLACEHOLDER_SECRET, 30, environment))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsPlaceholderSecretInTestProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test");

        assertThatCode(() -> new JwtTokenProvider(
                JwtTokenProvider.DEFAULT_PLACEHOLDER_SECRET, 30, environment))
                .doesNotThrowAnyException();
    }

    @Test
    void allowsRealSecretEvenWithNoActiveProfile() {
        MockEnvironment environment = new MockEnvironment();

        JwtTokenProvider provider = new JwtTokenProvider(REAL_SECRET, 30, environment);

        assertThat(provider).isNotNull();
    }
}
