package dev.hackercode.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_settings")
public class UserSettings {
    @Id
    private Long userId;
    @Column(nullable = false)
    private String settingsJson;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    protected UserSettings() {}

    public UserSettings(Long userId, String settingsJson) {
        this.userId = userId;
        this.settingsJson = settingsJson;
    }

    public Long getUserId() { return userId; }
    public String getSettingsJson() { return settingsJson; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void update(String json) {
        this.settingsJson = json;
        this.updatedAt = Instant.now();
    }
}
