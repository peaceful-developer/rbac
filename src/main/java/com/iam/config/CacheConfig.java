package com.iam.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Backs {@code CustomUserDetailsService} with a short-TTL cache so authenticated requests
 * don't hit the database on every call to re-resolve a user's roles/permissions. The TTL
 * bounds how long a role or lockout change can take to propagate to an already-issued
 * access token; callers that change roles/enabled/locked state evict the entry immediately
 * instead of waiting for expiry (see UserService).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String USER_DETAILS_CACHE = "userDetails";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(USER_DETAILS_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(10_000));
        return cacheManager;
    }
}
