package com.leslie.teachtrack.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiTokenAuthFilter extends OncePerRequestFilter {
    private final ApiTokenRepository repository;
    private final TokenService tokenService;

    public ApiTokenAuthFilter(ApiTokenRepository repository, TokenService tokenService) {
        this.repository = repository;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String raw = header.substring("Bearer ".length()).trim();
            if (!raw.isEmpty()) {
                authenticate(raw);
            }
        }
        chain.doFilter(request, response);
    }

    private void authenticate(String rawToken) {
        Optional<ApiTokenRepository.TokenRow> found = repository.findByHash(tokenService.hash(rawToken));
        if (found.isEmpty()) {
            return;
        }
        ApiTokenRepository.TokenRow row = found.get();
        if (!"ACTIVE".equals(row.userStatus())) {
            return;
        }
        Instant now = Instant.now();
        if (row.revokedAt() != null && row.revokedAt().isBefore(now)) {
            return;
        }
        if (row.expiresAt() != null && row.expiresAt().isBefore(now)) {
            return;
        }
        List<String> scopes = row.scopes();
        List<SimpleGrantedAuthority> authorities = scopes.stream()
                .map(scope -> new SimpleGrantedAuthority("SCOPE_" + scope))
                .toList();
        AuthPrincipal principal = new AuthPrincipal(row.userId().toString(), row.tokenId().toString(),
                new java.util.LinkedHashSet<>(scopes));
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        repository.touch(row.tokenId());
    }
}
