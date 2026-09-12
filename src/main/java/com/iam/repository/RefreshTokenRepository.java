package com.iam.repository;

import com.iam.domain.RefreshToken;
import com.iam.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true where rt.user = :user and rt.revoked = false")
    void revokeAllForUser(@Param("user") User user);

    @Modifying
    @Query("delete from RefreshToken rt where rt.expiresAt < CURRENT_TIMESTAMP")
    void deleteAllExpired();
}
