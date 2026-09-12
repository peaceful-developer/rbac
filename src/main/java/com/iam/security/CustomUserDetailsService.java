package com.iam.security;

import com.iam.config.CacheConfig;
import com.iam.domain.User;
import com.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Backs every authenticated request (see JwtAuthenticationFilter): the JWT itself only
 * proves who the caller claims to be, this resolves what they're currently allowed to do.
 * Results are cached briefly (see CacheConfig) so that resolution isn't a DB hit per request.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Cacheable(cacheNames = CacheConfig.USER_DETAILS_CACHE, key = "#username")
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findWithRolesByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("No user found with username: " + username));
        return new UserPrincipal(user);
    }
}
