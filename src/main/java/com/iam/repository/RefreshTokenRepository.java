package com.iam.repository;

import com.iam.domain.RefreshToken;
import com.iam.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /** Looked up by the hash of the raw token presented on refresh/logout - see RefreshTokenGenerator. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Bulk-revokes every still-valid refresh token for a user in one statement, used
     * on logout, password change, and user deletion - anywhere a user's other active
     * sessions need to be invalidated immediately rather than left to expire naturally.
     */
    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true where rt.user = :user and rt.revoked = false")
    void revokeAllForUser(@Param("user") User user);

    /**
     * Housekeeping: deletes rows past their expiry so the table doesn't grow forever.
     * Not currently invoked by any scheduled job in this codebase - wire it up to a
     * periodic task (e.g. {@code @Scheduled}) if that cleanup is needed in practice.
     */
    @Modifying
    @Query("delete from RefreshToken rt where rt.expiresAt < CURRENT_TIMESTAMP")
    void deleteAllExpired();
}
