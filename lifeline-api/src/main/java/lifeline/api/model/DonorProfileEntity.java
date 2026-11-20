package lifeline.api.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "donor_profiles")
public class DonorProfileEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private UserEntity user;

    @Column(name = "blood_group", nullable = false, length = 10)
    private String bloodGroup;

    @Column(nullable = false, length = 10)
    private String sex;

    @Column(name = "home_zone", nullable = false, length = 50)
    private String homeZone;

    @Column(name = "last_donation_date")
    private LocalDate lastDonationDate;

    @Column(name = "is_available", nullable = false)
    private boolean available = true;

    @Column(name = "is_paused", nullable = false)
    private boolean paused = false;

    @Column(name = "current_zone", length = 50)
    private String currentZone;

    @Column(name = "location_confirmed_at")
    private Instant locationConfirmedAt;

    @Column(name = "total_contacts", nullable = false)
    private int totalContacts = 0;

    @Column(name = "total_accepts", nullable = false)
    private int totalAccepts = 0;

    @Column(name = "total_declines", nullable = false)
    private int totalDeclines = 0;

    @Column(name = "total_no_responses", nullable = false)
    private int totalNoResponses = 0;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public DonorProfileEntity() {
    }

    public DonorProfileEntity(UserEntity user, String bloodGroup, String sex, String homeZone) {
        this.user = user;
        this.bloodGroup = bloodGroup;
        this.sex = sex;
        this.homeZone = homeZone;
        this.available = true;
        this.paused = false;
        this.currentZone = homeZone;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public UserEntity getUser() {
        return user;
    }

    public void setUser(UserEntity user) {
        this.user = user;
    }

    public String getBloodGroup() {
        return bloodGroup;
    }

    public void setBloodGroup(String bloodGroup) {
        this.bloodGroup = bloodGroup;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getHomeZone() {
        return homeZone;
    }

    public void setHomeZone(String homeZone) {
        this.homeZone = homeZone;
    }

    public LocalDate getLastDonationDate() {
        return lastDonationDate;
    }

    public void setLastDonationDate(LocalDate lastDonationDate) {
        this.lastDonationDate = lastDonationDate;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public String getCurrentZone() {
        return currentZone;
    }

    public void setCurrentZone(String currentZone) {
        this.currentZone = currentZone;
    }

    public Instant getLocationConfirmedAt() {
        return locationConfirmedAt;
    }

    public void setLocationConfirmedAt(Instant locationConfirmedAt) {
        this.locationConfirmedAt = locationConfirmedAt;
    }

    public int getTotalContacts() {
        return totalContacts;
    }

    public void setTotalContacts(int totalContacts) {
        this.totalContacts = totalContacts;
    }

    public int getTotalAccepts() {
        return totalAccepts;
    }

    public void setTotalAccepts(int totalAccepts) {
        this.totalAccepts = totalAccepts;
    }

    public int getTotalDeclines() {
        return totalDeclines;
    }

    public void setTotalDeclines(int totalDeclines) {
        this.totalDeclines = totalDeclines;
    }

    public int getTotalNoResponses() {
        return totalNoResponses;
    }

    public void setTotalNoResponses(int totalNoResponses) {
        this.totalNoResponses = totalNoResponses;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
