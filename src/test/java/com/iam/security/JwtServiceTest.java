package com.iam.security;

import com.iam.config.JwtProperties;
import com.iam.domain.Role;
import com.iam.domain.User;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private final JwtProperties properties = new JwtProperties(
            "unit-test-secret-key-that-is-long-enough-for-hs256-signing", 15, 7, "iam-service-test");
    private final JwtService jwtService = new JwtService(properties);

    private UserPrincipal principal() {
        User user = User.builder()
                .id(1L)
                .username("jdoe")
                .email("jdoe@example.com")
                .passwordHash("irrelevant")
                .roles(Set.of(Role.builder().id(1L).name("USER").permissions(Set.of()).build()))
                .build();
        return new UserPrincipal(user);
    }

    @Test
    void generatesTokenThatCanBeParsedBackToSameUsername() {
        String token = jwtService.generateAccessToken(principal());
        assertThat(jwtService.extractUsername(token)).isEqualTo("jdoe");
        assertThat(jwtService.isTokenValid(token, "jdoe")).isTrue();
    }

    @Test
    void tokenIsInvalidForADifferentUsername() {
        String token = jwtService.generateAccessToken(principal());
        assertThat(jwtService.isTokenValid(token, "someone-else")).isFalse();
    }

    @Test
    void expiredTokenFailsToParse() {
        JwtProperties expiredNow = new JwtProperties(properties.secret(), 0, 7, properties.issuer());
        JwtService expiredService = new JwtService(expiredNow);
        String token = expiredService.generateAccessToken(principal());

        assertThatThrownBy(() -> jwtService.parseClaims(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
