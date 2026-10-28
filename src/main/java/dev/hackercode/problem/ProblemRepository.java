package dev.hackercode.problem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProblemRepository extends JpaRepository<Problem, String> {
    Optional<Problem> findBySlug(String slug);

    List<Problem> findAllByOrderByPositionAsc();

    boolean existsBySlug(String slug);

    @Query("select coalesce(max(p.position), 0) from Problem p")
    int maxPosition();
}
