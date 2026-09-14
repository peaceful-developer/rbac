package com.iam.security;

import com.iam.exception.ErrorResponseWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Runs once per request (see {@link OncePerRequestFilter}), before the request reaches
 * any controller. Reads the {@code Authorization: Bearer <token>} header, and if present
 * and valid, populates the {@link SecurityContextHolder} so downstream
 * {@code @PreAuthorize} checks and {@code @AuthenticationPrincipal} injections work.
 * <p>
 * Registered in {@code SecurityConfig} ahead of
 * {@code UsernamePasswordAuthenticationFilter} in the filter chain, since this app
 * never uses that form-login filter - JWT is the only authentication mechanism.
 * Requests with no/invalid header simply proceed unauthenticated; it's
 * {@code SecurityConfig}'s {@code authorizeHttpRequests} rules (enforced later in the
 * chain) that actually reject them if the endpoint requires authentication.
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final ErrorResponseWriter errorResponseWriter;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        // No bearer token at all - not this filter's problem, just pass through.
        // (Public endpoints proceed fine; protected ones get rejected further down the chain.)
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = header.substring(BEARER_PREFIX.length());

        try {
            String username = jwtService.extractUsername(token);

            // The "authentication == null" check avoids redundant work if something
            // earlier in the chain already authenticated this request.
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                // This is the (cached) DB-backed lookup that resolves *current*
                // roles/permissions - see CustomUserDetailsService for why the JWT's
                // own embedded authorities claim isn't used here.
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (jwtService.isTokenValid(token, userDetails.getUsername()) && userDetails.isEnabled()
                        && userDetails.isAccountNonLocked()) {
                    UsernamePasswordAuthenticationToken authToken =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
                // else: token structurally valid but the account is now disabled/locked,
                // or the token doesn't match this user - request proceeds unauthenticated.
            }
            filterChain.doFilter(request, response);
        } catch (JwtException | IllegalArgumentException ex) {
            // Malformed, expired, or signature-invalid token: short-circuit with 401
            // instead of letting the request continue unauthenticated, so the client
            // gets a clear signal to re-authenticate rather than a confusing 403 later.
            errorResponseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired access token");
        }
    }
}
