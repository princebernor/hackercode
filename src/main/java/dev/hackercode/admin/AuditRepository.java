package dev.hackercode.admin;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditRepository extends JpaRepository<AuditEvent, Long> {
    @Query("""
            select a from AuditEvent a
            where (:action is null or a.action = :action)
              and (:prefix is null or a.action like concat(:prefix, '%'))
            order by a.createdAt desc
            """)
    Page<AuditEvent> search(@Param("action") String action, @Param("prefix") String prefix, Pageable pageable);

    List<AuditEvent> findTop10ByOrderByCreatedAtDesc();

    long countByActionStartingWithAndCreatedAtAfter(String prefix, Instant since);

    @Modifying
    @Query("delete from AuditEvent a where a.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
