package com.iam.service;

import com.iam.config.JwtProperties;
import com.iam.domain.RefreshToken;
import com.iam.domain.Role;
import com.iam.domain.User;
import com.iam.dto.request.LoginRequest;
import com.iam.dto.request.RegisterRequest;
import com.iam.dto.response.AuthResponse;
import com.iam.exception.DuplicateResourceException;
import com.iam.exception.InvalidRefreshTokenException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.RefreshTokenRepository;
import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import com.iam.security.JwtService;
import com.iam.security.RefreshTokenGenerator;
import com.iam.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

/**
 * Everything under {@code /api/auth}: registration, login, refresh-token exchange, and
 * logout. This is the one place that mints tokens - see {@link #issueTokens} - and the
 * one place that validates/rotates/revokes refresh tokens.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** Role every self-registered account gets; must exist in the roles table (seeded by V2__seed_data.sql). */
    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtProperties jwtProperties;

    /** Creates a new account with the default {@code USER} role and immediately logs it in (same response shape as {@link #login}). */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
        }

        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new ResourceNotFoundException("Default role '" + DEFAULT_ROLE + "' is not configured"));

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .roles(Set.of(defaultRole))
                .build();

        user = userRepository.save(user);
        return issueTokens(new UserPrincipal(user));
    }

    /**
     * Delegates credential verification to Spring Security's {@link AuthenticationManager}
     * (configured in SecurityConfig with a {@code DaoAuthenticationProvider} backed by
     * {@code CustomUserDetailsService} and the BCrypt {@code PasswordEncoder}). This also
     * transparently enforces the account's enabled/locked flags - a disabled or locked
     * account fails here with the corresponding Spring Security exception, translated to
     * a 401/403 by {@code GlobalExceptionHandler}.
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return issueTokens(principal);
    }

    /**
     * Exchanges a still-valid refresh token for a brand-new access/refresh pair, and
     * revokes the one that was presented in the same step - so a given raw refresh
     * token value can only ever be used once ("rotation"). This also means presenting
     * an already-used (or otherwise invalid/expired/revoked) token always fails, which
     * is a simple form of reuse detection: if a stolen token gets replayed after the
     * legitimate client already rotated it, the replay is rejected.
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = refreshTokenGenerator.hash(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));

        if (storedToken.isRevoked() || storedToken.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token is expired or has been revoked");
        }

        // Rotate immediately, before issuing the replacement - if anything below fails,
        // the old token stays revoked (fail-closed) rather than remaining usable.
        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = userRepository.findWithRolesById(storedToken.getUser().getId())
                .orElseThrow(() -> new InvalidRefreshTokenException("The user for this token no longer exists"));

        // Re-checked here (not just at login): an admin could have disabled/locked the
        // account since this refresh token was issued, and refreshing is how that gets
        // enforced without waiting for the access token to expire on its own.
        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new InvalidRefreshTokenException("This account can no longer authenticate");
        }

        return issueTokens(new UserPrincipal(user));
    }

    /**
     * Revokes a single refresh token (used on sign-out). Silently no-ops if the token
     * doesn't match anything - logout should never fail just because the client's
     * local token was already stale.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = refreshTokenGenerator.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    /** Shared by register/login/refresh: mints one access token (JWT) and one opaque refresh token, persisting only the refresh token's hash. */
    private AuthResponse issueTokens(UserPrincipal principal) {
        String accessToken = jwtService.generateAccessToken(principal);

        String rawRefreshToken = refreshTokenGenerator.generateRawToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(userRepository.getReferenceById(principal.getId()))
                .tokenHash(refreshTokenGenerator.hash(rawRefreshToken))
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtlDays(), ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(refreshToken);

        // The raw (unhashed) value is returned to the caller exactly once, here - it is
        // never persisted or logged, only its hash is (see RefreshToken.tokenHash).
        return AuthResponse.of(accessToken, rawRefreshToken, jwtService.getAccessTokenTtlSeconds());
    }
}
