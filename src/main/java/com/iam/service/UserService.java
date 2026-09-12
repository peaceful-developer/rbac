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

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.USER_DETAILS_CACHE, key = "#result.username")
    public User assignRoles(Long id, AssignRolesRequest request) {
        User user = getById(id);
        user.setRoles(resolveRoles(request.roles()));
        return userRepository.save(user);
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = getById(id);
        refreshTokenRepository.revokeAllForUser(user);
        userRepository.delete(user);
        evictUserDetailsCache(user.getUsername());
    }

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

    private void evictUserDetailsCache(String username) {
        var cache = cacheManager.getCache(CacheConfig.USER_DETAILS_CACHE);
        if (cache != null) {
            cache.evict(username);
        }
    }

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
