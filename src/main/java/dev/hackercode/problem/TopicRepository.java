package dev.hackercode.problem;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, Long> {
    Optional<Topic> findBySlug(String slug);

    List<Topic> findAllByOrderByPositionAsc();
}
