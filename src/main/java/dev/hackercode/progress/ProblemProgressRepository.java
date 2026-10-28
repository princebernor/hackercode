package dev.hackercode.progress;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProblemProgressRepository extends JpaRepository<ProblemProgress, Long> {
    List<ProblemProgress> findByUserId(Long userId);

    Optional<ProblemProgress> findByUserIdAndProblemId(Long userId, String problemId);

    List<ProblemProgress> findTop8ByUserIdAndLastViewedAtIsNotNullOrderByLastViewedAtDesc(Long userId);

    List<ProblemProgress> findByUserIdAndBookmarkedTrueOrderByUpdatedAtDesc(Long userId);

    long countByStatus(ProblemProgress.Status status);

    @Query("select count(distinct p.userId) from ProblemProgress p where p.status <> dev.hackercode.progress.ProblemProgress.Status.NOT_STARTED")
    long countActiveLearners();
}
