package dev.hackercode.activity;

import dev.hackercode.user.PreferencesService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records learning activity, honouring the user's "track my activity" privacy preference. */
@Service
public class ActivityService {
    private final ActivityRepository repository;
    private final PreferencesService preferences;

    public ActivityService(ActivityRepository repository, PreferencesService preferences) {
        this.repository = repository;
        this.preferences = preferences;
    }

    @Transactional
    public void record(Long userId, ActivityEvent.Type type, String problemId, String language) {
        if (!preferences.get(userId).privacy().trackActivity()) return;
        repository.save(new ActivityEvent(userId, type, problemId, language));
    }
}
