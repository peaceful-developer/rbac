package com.iam.security;

import com.iam.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Issues and validates short-lived JWT access tokens. Refresh tokens are opaque
 * random values persisted (hashed) server-side, see {@link com.iam.service.AuthService}.
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String AUTHORITIES_CLAIM = "authorities";
    private static final String USER_ID_CLAIM = "uid";

    private final JwtProperties jwtProperties;

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.accessTokenTtlMinutes(), ChronoUnit.MINUTES);

        // Included for client convenience (e.g. a frontend rendering UI without an extra call).
        // JwtAuthenticationFilter does NOT trust this claim for authorization decisions - it
        // re-resolves authorities from CustomUserDetailsService (cached, see CacheConfig) on
        // every request, so a role/lockout change is enforced well before this token expires.
        List<String> authorities = principal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return Jwts.builder()
                .issuer(jwtProperties.issuer())
                .subject(principal.getUsername())
                .claim(USER_ID_CLAIM, principal.getId())
                .claim(AUTHORITIES_CLAIM, authorities)
                .issuedAt(java.util.Date.from(now))
                .expiration(java.util.Date.from(expiry))
                .signWith(signingKey())
                .compact();
    }

    public Claims parseClaims(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, String expectedUsername) {
        Claims claims = parseClaims(token);
        return claims.getSubject().equals(expectedUsername) && claims.getExpiration().after(new java.util.Date());
    }

    public long getAccessTokenTtlSeconds() {
        return jwtProperties.accessTokenTtlMinutes() * 60;
    }
}
