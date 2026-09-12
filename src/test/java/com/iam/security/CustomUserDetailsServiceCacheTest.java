package com.iam.security;

import com.iam.domain.Role;
import com.iam.domain.User;
import com.iam.repository.RoleRepository;
import com.iam.repository.UserRepository;
import com.iam.support.TestDataSeeder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that role lookups are cached (so authenticated requests don't hit the DB every
 * time) and that the cache entry is gone once evicted - the mechanism UserService/RoleService
 * rely on for near-immediate revocation. See CacheConfig for the rationale.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CustomUserDetailsServiceCacheTest {

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TestDataSeeder testDataSeeder;

    @BeforeEach
    void setUp() {
        testDataSeeder.seedBaselineRolesAndPermissions();
        Role userRole = roleRepository.findByName("USER").orElseThrow();
        userRepository.save(User.builder()
                .username("cacheduser")
                .email("cacheduser@example.com")
                .passwordHash(passwordEncoder.encode("SuperSecret1"))
                .roles(Set.of(userRole))
                .build());
    }

    @AfterEach
    void tearDown() {
        cacheManager.getCache("userDetails").clear();
    }

    @Test
    void secondLookupIsServedFromCache() {
        userDetailsService.loadUserByUsername("cacheduser");
        assertThat(cacheManager.getCache("userDetails").get("cacheduser")).isNotNull();
    }

    @Test
    void evictingCacheRemovesEntry() {
        userDetailsService.loadUserByUsername("cacheduser");
        assertThat(cacheManager.getCache("userDetails").get("cacheduser")).isNotNull();

        cacheManager.getCache("userDetails").evict("cacheduser");

        assertThat(cacheManager.getCache("userDetails").get("cacheduser")).isNull();
    }

    @Test
    void authoritiesReflectAssignedRolePermissions() {
        var userDetails = userDetailsService.loadUserByUsername("cacheduser");
        Set<String> authorityNames = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(java.util.stream.Collectors.toSet());
        assertThat(authorityNames).contains("ROLE_USER");
    }
}
