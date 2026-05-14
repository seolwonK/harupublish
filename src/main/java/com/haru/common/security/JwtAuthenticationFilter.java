package com.haru.common.security;

import com.haru.common.exception.HaruException;
import com.haru.user.domain.Role;
import com.haru.user.domain.UserAccount;
import com.haru.user.infra.UserAccountRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserAccountRepository userAccountRepository;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, UserAccountRepository userAccountRepository) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Claims claims = jwtTokenProvider.parseClaims(authorization.substring(BEARER_PREFIX.length()));
            Long userId = Long.valueOf(claims.getSubject());
            UserAccount user = userAccountRepository.findWithRolesById(userId).orElse(null);
            if (user != null) {
                user.ensureActive();
                HaruPrincipal principal = new HaruPrincipal(
                        user.getId(),
                        user.getEmail(),
                        user.getRoles(),
                        user.getActiveRole()
                );
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        toAuthorities(user.getRoles())
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (JwtException | IllegalArgumentException | HaruException ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private Set<org.springframework.security.core.GrantedAuthority> toAuthorities(Set<Role> roles) {
        return roles.stream()
                .map(Role::authority)
                .collect(Collectors.toSet());
    }
}
