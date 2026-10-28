package dev.hackercode.user;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferencesService {
    private static final Logger log = LoggerFactory.getLogger(PreferencesService.class);

    private final UserSettingsRepository repository;
    private final ObjectMapper json;

    public PreferencesService(UserSettingsRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public UserPreferences get(Long userId) {
        return repository.findById(userId).map(s -> {
            try {
                return json.readValue(s.getSettingsJson(), UserPreferences.class).validated();
            } catch (Exception e) {
                log.warn("Stored preferences for user {} were unreadable; falling back to defaults", userId);
                return UserPreferences.defaults();
            }
        }).orElseGet(UserPreferences::defaults);
    }

    @Transactional
    public UserPreferences save(Long userId, UserPreferences prefs) {
        UserPreferences valid = prefs.validated();
        String body;
        try {
            body = json.writeValueAsString(valid);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
        repository.findById(userId).ifPresentOrElse(s -> s.update(body), () -> repository.save(new UserSettings(userId, body)));
        return valid;
    }
}
