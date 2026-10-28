package dev.hackercode.auth;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    Optional<AuthToken> findByTokenHashAndType(String tokenHash, AuthToken.Type type);

    @Modifying
    @Query("update AuthToken t set t.usedAt = :now where t.userId = :userId and t.type = :type and t.usedAt is null")
    int invalidateAll(@Param("userId") Long userId, @Param("type") AuthToken.Type type, @Param("now") Instant now);

    @Modifying
    @Query("delete from AuthToken t where t.expiresAt < :before")
    int deleteExpired(@Param("before") Instant before);
}
