package dev.hackercode.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserSessionRepository extends JpaRepository<UserSession, Long> {
    Optional<UserSession> findBySessionKey(String sessionKey);

    List<UserSession> findByUserIdAndRevokedAtIsNullOrderByLastSeenAtDesc(Long userId);

    long countByLastSeenAtAfterAndRevokedAtIsNull(Instant since);

    @Modifying
    @Query("update UserSession s set s.revokedAt = :now where s.userId = :userId and s.revokedAt is null and s.sessionKey <> :keep")
    int revokeAllExcept(@Param("userId") Long userId, @Param("keep") String keepSessionKey, @Param("now") Instant now);

    @Modifying
    @Query("delete from UserSession s where s.lastSeenAt < :before")
    int deleteStale(@Param("before") Instant before);
}
