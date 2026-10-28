package dev.hackercode.review;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodeReviewRepository extends JpaRepository<CodeReview, Long> {
    List<CodeReview> findTop20ByUserIdOrderByCreatedAtDesc(Long userId);

    List<CodeReview> findTop5ByUserIdOrderByCreatedAtDesc(Long userId);

    List<CodeReview> findByUserId(Long userId);

    Optional<CodeReview> findByIdAndUserId(Long id, Long userId);

    long countByCreatedAtAfter(Instant since);

    long countByAiGeneratedTrueAndCreatedAtAfter(Instant since);

    @Modifying
    @Query("delete from CodeReview r where r.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
