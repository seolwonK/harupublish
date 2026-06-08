package com.haru.common.security;

import com.haru.common.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;
    private final List<String> corsAllowedOriginPatterns;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectMapper objectMapper,
            @Value("${haru.cors.allowed-origin-patterns}") String corsAllowedOriginPatterns
    ) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
        this.corsAllowedOriginPatterns = resolveCorsPatterns(corsAllowedOriginPatterns);
    }

    /** Shared by the HTTP CORS config and the STOMP handshake endpoint. */
    public static List<String> resolveCorsPatterns(String rawPatterns) {
        List<String> configuredPatterns = Arrays.stream(rawPatterns.split(","))
                .map(String::trim)
                .map(SecurityConfig::stripWrappingQuotes)
                .map(SecurityConfig::stripTrailingSlash)
                .filter(origin -> !origin.isEmpty())
                .toList();
        return withDefaultCorsPatterns(configuredPatterns);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .headers(headers -> headers.frameOptions(frameOptions -> frameOptions.sameOrigin()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/",
                                "/test-ui.html",
                                "/uploads/**",
                                "/ws-chat/**",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs",
                                "/v3/api-docs/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhooks/lemonsqueezy", "/api/payments/webhooks/lemon-squeezy").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/exchange-rates/latest").permitAll()
                        // Dev test-data tooling. Open at the HTTP layer; the controller itself is
                        // inert (404) unless haru.dev.enabled=true, which is the real on/off switch.
                        .requestMatchers("/api/dev/**").permitAll()
                        .requestMatchers("/api/tutors/me/**").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/tutors", "/api/tutors/*", "/api/tutors/*/schedule", "/api/tutors/*/reviews").permitAll()
                        .requestMatchers("/api/auth/signup", "/api/auth/login", "/api/auth/refresh").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint((request, response, authException) ->
                                writeError(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required."))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                writeError(response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN", "Access is denied."))
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsAllowedOriginPatterns);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    private void writeError(HttpServletResponse response, int status, String code, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(code, message));
    }

    private static List<String> withDefaultCorsPatterns(List<String> configuredPatterns) {
        List<String> patterns = new ArrayList<>(configuredPatterns);
        addIfMissing(patterns, "https://*.vercel.app");
        addIfMissing(patterns, "http://localhost:*");
        addIfMissing(patterns, "http://127.0.0.1:*");
        return List.copyOf(patterns);
    }

    private static void addIfMissing(List<String> patterns, String pattern) {
        if (!patterns.contains(pattern)) {
            patterns.add(pattern);
        }
    }

    private static String stripWrappingQuotes(String value) {
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static String stripTrailingSlash(String value) {
        if (value.length() > "https://".length() && value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
