package dev.hackercode.activity;

import dev.hackercode.auth.MailService;
import dev.hackercode.user.PreferencesService;
import dev.hackercode.user.User;
import dev.hackercode.user.UserPreferences;
import dev.hackercode.user.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Sends the daily learning reminder (Settings → Learning) to users who enabled it and have not practised yet today.
 * Reminder times are UTC. It posts an in-app notification, and an email when SMTP is configured and the user
 * opted in to reminder emails. Each user gets at most one reminder per day.
 */
@Component
public class LearningReminderJob {
    static final int WINDOW_MINUTES = 15;

    private final UserRepository users;
    private final PreferencesService preferences;
    private final ActivityRepository activity;
    private final NotificationRepository notifications;
    private final MailService mail;
    private final Map<Long, LocalDate> lastSent = new ConcurrentHashMap<>();

    public LearningReminderJob(UserRepository users, PreferencesService preferences, ActivityRepository activity,
                               NotificationRepository notifications, MailService mail) {
        this.users = users;
        this.preferences = preferences;
        this.activity = activity;
        this.notifications = notifications;
        this.mail = mail;
    }

    @Scheduled(cron = "0 */15 * * * *", zone = "UTC")
    public void run() {
        LocalTime now = LocalTime.now(ZoneOffset.UTC);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        for (User user : users.findAll()) {
            if (user.getStatus() != User.Status.ACTIVE || today.equals(lastSent.get(user.getId()))) continue;
            UserPreferences prefs = preferences.get(user.getId());
            if (!prefs.learning().remindersEnabled() || !due(prefs.learning().reminderTime(), now)) continue;
            if (activity.activeDays(user.getId(), today).contains(today)) continue;
            lastSent.put(user.getId(), today);
            String body = "Your goal today is " + prefs.learning().dailyGoal() + " problem(s). A short session keeps your streak alive.";
            if (prefs.notifications().inApp()) notifications.save(new Notification(user.getId(), "Time to practice", body));
            if (prefs.notifications().emailReminders() && user.isEmailVerified() && mail.mode() == MailService.Mode.SMTP) {
                mail.send(user.getEmail(), "Your HackerCode practice reminder", body + "\n\n" + mail.link("/dashboard")
                        + "\n\nTurn reminders off any time in Settings → Learning.");
            }
        }
    }

    /** True when the reminder time falls inside the current 15-minute window. */
    static boolean due(String reminderTime, LocalTime now) {
        LocalTime t = LocalTime.parse(reminderTime);
        LocalTime windowStart = now.minusMinutes(WINDOW_MINUTES);
        return windowStart.isBefore(now) ? !t.isBefore(windowStart) && t.isBefore(now) : !t.isBefore(windowStart) || t.isBefore(now);
    }
}
