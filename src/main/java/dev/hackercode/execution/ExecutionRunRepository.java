package dev.hackercode.execution;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExecutionRunRepository extends JpaRepository<ExecutionRun, Long> {
    List<ExecutionRun> findTop20ByUserIdAndProblemIdAndKindOrderByCreatedAtDesc(Long userId, String problemId, String kind);

    List<ExecutionRun> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    List<ExecutionRun> findByUserId(Long userId);

    long countByCreatedAtAfter(Instant since);

    long countByStatusAndCreatedAtAfter(String status, Instant since);

    List<ExecutionRun> findTop20ByStatusOrderByCreatedAtDesc(String status);

    /** Plain runs are short-lived; submissions (saved solutions) are kept. */
    @Modifying
    @Query("delete from ExecutionRun r where r.kind = 'RUN' and r.createdAt < :before")
    int deleteRunsOlderThan(@Param("before") Instant before);
}
