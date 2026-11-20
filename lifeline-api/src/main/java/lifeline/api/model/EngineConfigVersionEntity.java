package lifeline.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "engine_config_versions")
public class EngineConfigVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_number", nullable = false, unique = true)
    private int versionNumber;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "config_json", nullable = false, columnDefinition = "TEXT")
    private String configJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_id")
    private UserEntity author;

    @Column(name = "is_active", nullable = false)
    private boolean active = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public EngineConfigVersionEntity() {
    }

    public EngineConfigVersionEntity(int versionNumber, String description, String configJson, UserEntity author, boolean active) {
        this.versionNumber = versionNumber;
        this.description = description;
        this.configJson = configJson;
        this.author = author;
        this.active = active;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(int versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getConfigJson() {
        return configJson;
    }

    public void setConfigJson(String configJson) {
        this.configJson = configJson;
    }

    public UserEntity getAuthor() {
        return author;
    }

    public void setAuthor(UserEntity author) {
        this.author = author;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
