package dev.hackercode.activity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LearningReminderJobTest {

    @Test
    void reminderIsDueOnlyInsideTheCurrentWindow() {
        assertThat(LearningReminderJob.due("19:00", LocalTime.of(19, 10))).isTrue();
        assertThat(LearningReminderJob.due("19:00", LocalTime.of(19, 0))).isFalse();
        assertThat(LearningReminderJob.due("19:00", LocalTime.of(19, 15))).isTrue();
        assertThat(LearningReminderJob.due("19:00", LocalTime.of(19, 16))).isFalse();
        // Window wrapping midnight.
        assertThat(LearningReminderJob.due("23:55", LocalTime.of(0, 5))).isTrue();
    }

    @Test
    void streaksCountConsecutiveDaysEndingTodayOrYesterday() {
        LocalDate today = LocalDate.of(2026, 10, 27);
        Set<LocalDate> days = Set.of(today, today.minusDays(1), today.minusDays(2), today.minusDays(5));
        assertThat(DashboardController.currentStreak(days, today)).isEqualTo(3);
        assertThat(DashboardController.currentStreak(Set.of(today.minusDays(1)), today)).isEqualTo(1);
        assertThat(DashboardController.currentStreak(Set.of(today.minusDays(2)), today)).isZero();
        assertThat(DashboardController.longestStreak(days)).isEqualTo(3);
    }
}
