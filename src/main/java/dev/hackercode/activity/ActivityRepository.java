package dev.hackercode.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ActivityRepository extends JpaRepository<ActivityEvent, Long> {

    interface DayCount {
        LocalDate getDay();
        long getCount();
    }

    interface LanguageCount {
        String getLanguage();
        long getCount();
    }

    @Query("select a.occurredOn as day, count(a) as count from ActivityEvent a where a.userId = :userId and a.occurredOn >= :since group by a.occurredOn")
    List<DayCount> countByDay(@Param("userId") Long userId, @Param("since") LocalDate since);

    @Query("select a.language as language, count(a) as count from ActivityEvent a where a.userId = :userId and a.language is not null and a.type in (dev.hackercode.activity.ActivityEvent.Type.CODE_RUN, dev.hackercode.activity.ActivityEvent.Type.CODE_SUBMITTED, dev.hackercode.activity.ActivityEvent.Type.REVIEW_REQUESTED) group by a.language")
    List<LanguageCount> countByLanguage(@Param("userId") Long userId);

    @Query("select distinct a.occurredOn from ActivityEvent a where a.userId = :userId and a.occurredOn >= :since")
    List<LocalDate> activeDays(@Param("userId") Long userId, @Param("since") LocalDate since);

    List<ActivityEvent> findTop15ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByCreatedAtAfter(Instant since);

    long countByTypeAndCreatedAtAfter(ActivityEvent.Type type, Instant since);

    @Query("select count(distinct a.userId) from ActivityEvent a where a.createdAt > :since")
    long countDistinctUsersSince(@Param("since") Instant since);

    @Modifying
    @Query("delete from ActivityEvent a where a.createdAt < :before")
    int deleteOlderThan(@Param("before") Instant before);
}
