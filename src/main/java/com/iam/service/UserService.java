package com.iam.service;

import com.iam.config.CacheConfig;
import com.iam.domain.Role;
import com.iam.domain.User;
import com.iam.dto.request.AssignRolesRequest;
import com.iam.dto.request.ChangePasswordRequest;
import com.iam.dto.request.CreateUserRequest;
import com.iam.dto.request.UpdateUserRequest;
import com.iam.exception.BadRequestException;
import com.iam.exception.DuplicateResourceException;
import com.iam.exception.ResourceNotFoundException;
import com.iam.repository.RefreshTokenRepository;
import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin user-management (list/create/update/assign-roles/delete) plus the
 * self-service "change my own password" operation. Backs the {@code /api/users}
 * endpoints - see UserController for which operations require which permission.
 * <p>
 * Several methods here evict the cached authorization data (see CacheConfig) for the
 * affected user so that role/enabled/locked changes are enforced on that user's very
 * next request rather than only after the cache's TTL expires.
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final CacheManager cacheManager;

    @Transactional(readOnly = true)
    public Page<User> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public User getById(Long id) {
        return userRepository.findWithRolesById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findWithRolesByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    /** Admin-side account creation - unlike self-registration, the caller can assign an initial role set directly. */
    @Transactional
    public User createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new DuplicateResourceException("Username '" + request.username() + "' is already taken");
        }
        if (userRepository.existsByEmail(request.email())) {
            throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
        }

        Set<Role> roles = resolveRoles(request.roles());

        User user = User.builder()
                .username(request.username())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .firstName(request.firstName())
                .lastName(request.lastName())
                .roles(roles)
                .build();

        return userRepository.save(user);
    }

    /**
     * Partial update: every field on {@link UpdateUserRequest} is optional and only
     * applied if present, so callers can change just e.g. {@code enabled} without
     * having to resend the whole profile. Cache eviction here is what makes toggling
     * {@code enabled}/{@code accountNonLocked} take effect immediately instead of
     * waiting out the cache TTL.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_DETAILS_CACHE, key = "#result.username")
    public User updateUser(Long id, UpdateUserRequest request) {
        User user = getById(id);

        if (request.email() != null && !request.email().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email())) {
                throw new DuplicateResourceException("Email '" + request.email() + "' is already registered");
            }
            user.setEmail(request.email());
        }
        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }
        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.accountNonLocked() != null) {
            user.setAccountNonLocked(request.accountNonLocked());
        }

        return userRepository.save(user);
    }

    /** Replaces (not merges with) the user's entire role set - see {@link #resolveRoles}. */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_DETAILS_CACHE, key = "#result.username")
    public User assignRoles(Long id, AssignRolesRequest request) {
        User user = getById(id);
        user.setRoles(resolveRoles(request.roles()));
        return userRepository.save(user);
    }

    /**
     * Grants or revokes the platform-level Master Admin flag (see {@code User#isMasterAdmin}).
     * Deliberately not exposed through {@link #updateUser} - this is a much higher-stakes
     * change and controller-gated to Master-Admin-only callers (see UserController), so
     * only an existing Master Admin can ever create another one. Evicts the cache entry
     * since "MASTER_ADMIN" is embedded as an authority (see UserPrincipal) and would
     * otherwise only take effect once the 60s cache TTL lapses.
     */
    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_DETAILS_CACHE, key = "#result.username")
    public User setMasterAdmin(Long id, boolean masterAdmin) {
        User user = getById(id);
        user.setMasterAdmin(masterAdmin);
        return userRepository.save(user);
    }

    /** Deletes the account and revokes any outstanding refresh tokens/cache entry so a deleted user can't keep a session alive. */
    @Transactional
    public void deleteUser(Long id) {
        User user = getById(id);
        refreshTokenRepository.revokeAllForUser(user);
        userRepository.delete(user);
        evictUserDetailsCache(user.getUsername());
    }

    /**
     * Self-service password change (the caller changes their own password, verified
     * against their current one - there is no admin "reset another user's password"
     * endpoint). Revokes all of the user's refresh tokens afterward: this device's
     * current access token remains valid until it naturally expires, but every other
     * signed-in device/session is forced to log in again with the new password.
     */
    @Transactional
    public void changePassword(Long id, ChangePasswordRequest request) {
        User user = getById(id);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user);
    }

    /** Used where @CacheEvict's SpEL can't apply (deleteUser has no return value to key off of). */
    private void evictUserDetailsCache(String username) {
        var cache = cacheManager.getCache(CacheConfig.USER_DETAILS_CACHE);
        if (cache != null) {
            cache.evict(username);
        }
    }

    /** Looks up each requested role by name, or falls back to the default USER role if none were specified. Every name must already exist. */
    private Set<Role> resolveRoles(Set<String> roleNames) {
        if (roleNames == null || roleNames.isEmpty()) {
            Role defaultRole = roleRepository.findByName("USER")
                    .orElseThrow(() -> new ResourceNotFoundException("Default role 'USER' is not configured"));
            return new HashSet<>(Set.of(defaultRole));
        }
        return roleNames.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + name)))
                .collect(Collectors.toCollection(HashSet::new));
    }
}
