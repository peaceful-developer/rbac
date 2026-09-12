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

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_ROLE = "USER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenGenerator refreshTokenGenerator;
    private final JwtProperties jwtProperties;

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

    @Transactional
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        return issueTokens(principal);
    }

    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        String hash = refreshTokenGenerator.hash(rawRefreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException("Refresh token is invalid"));

        if (storedToken.isRevoked() || storedToken.isExpired()) {
            throw new InvalidRefreshTokenException("Refresh token is expired or has been revoked");
        }

        storedToken.setRevoked(true);
        refreshTokenRepository.save(storedToken);

        User user = userRepository.findWithRolesById(storedToken.getUser().getId())
                .orElseThrow(() -> new InvalidRefreshTokenException("The user for this token no longer exists"));

        if (!user.isEnabled() || !user.isAccountNonLocked()) {
            throw new InvalidRefreshTokenException("This account can no longer authenticate");
        }

        return issueTokens(new UserPrincipal(user));
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        String hash = refreshTokenGenerator.hash(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(hash).ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });
    }

    private AuthResponse issueTokens(UserPrincipal principal) {
        String accessToken = jwtService.generateAccessToken(principal);

        String rawRefreshToken = refreshTokenGenerator.generateRawToken();
        RefreshToken refreshToken = RefreshToken.builder()
                .user(userRepository.getReferenceById(principal.getId()))
                .tokenHash(refreshTokenGenerator.hash(rawRefreshToken))
                .expiresAt(Instant.now().plus(jwtProperties.refreshTokenTtlDays(), ChronoUnit.DAYS))
                .build();
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(accessToken, rawRefreshToken, jwtService.getAccessTokenTtlSeconds());
    }
}
