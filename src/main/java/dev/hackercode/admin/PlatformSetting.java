package dev.hackercode.admin;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "platform_setting")
public class PlatformSetting {
    @Id
    @Column(name = "setting_key")
    private String key;
    @Column(name = "setting_value", nullable = false)
    private String value;
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
    private String updatedBy;

    protected PlatformSetting() {}

    public PlatformSetting(String key, String value, String updatedBy) {
        this.key = key;
        this.value = value;
        this.updatedBy = updatedBy;
    }

    public String getKey() { return key; }
    public String getValue() { return value; }
    public Instant getUpdatedAt() { return updatedAt; }
    public String getUpdatedBy() { return updatedBy; }

    public void update(String value, String updatedBy) {
        this.value = value;
        this.updatedBy = updatedBy;
        this.updatedAt = Instant.now();
    }
}
