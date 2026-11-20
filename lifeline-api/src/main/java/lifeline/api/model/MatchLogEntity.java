package lifeline.api.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "match_logs")
public class MatchLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "request_id", nullable = false)
    private BloodRequestEntity request;

    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "donor_id", nullable = false)
    private DonorProfileEntity donor;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(name = "total_score", nullable = false)
    private double totalScore;

    @Column(name = "factor_breakdown", nullable = false, columnDefinition = "TEXT")
    private String factorBreakdown;

    @Column(name = "explanation_text", nullable = false, columnDefinition = "TEXT")
    private String explanationText;

    @Column(name = "reason_code", nullable = false, length = 100)
    private String reasonCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private MatchOutcome outcome = MatchOutcome.NOTIFIED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "config_version_id")
    private EngineConfigVersionEntity configVersion;

    @Column(name = "notified_at", nullable = false)
    private Instant notifiedAt = Instant.now();

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public MatchLogEntity() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public BloodRequestEntity getRequest() {
        return request;
    }

    public void setRequest(BloodRequestEntity request) {
        this.request = request;
    }

    public int getStepNumber() {
        return stepNumber;
    }

    public void setStepNumber(int stepNumber) {
        this.stepNumber = stepNumber;
    }

    public DonorProfileEntity getDonor() {
        return donor;
    }

    public void setDonor(DonorProfileEntity donor) {
        this.donor = donor;
    }

    public int getRankPosition() {
        return rankPosition;
    }

    public void setRankPosition(int rankPosition) {
        this.rankPosition = rankPosition;
    }

    public double getTotalScore() {
        return totalScore;
    }

    public void setTotalScore(double totalScore) {
        this.totalScore = totalScore;
    }

    public String getFactorBreakdown() {
        return factorBreakdown;
    }

    public void setFactorBreakdown(String factorBreakdown) {
        this.factorBreakdown = factorBreakdown;
    }

    public String getExplanationText() {
        return explanationText;
    }

    public void setExplanationText(String explanationText) {
        this.explanationText = explanationText;
    }

    public String getReasonCode() {
        return reasonCode;
    }

    public void setReasonCode(String reasonCode) {
        this.reasonCode = reasonCode;
    }

    public MatchOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(MatchOutcome outcome) {
        this.outcome = outcome;
    }

    public EngineConfigVersionEntity getConfigVersion() {
        return configVersion;
    }

    public void setConfigVersion(EngineConfigVersionEntity configVersion) {
        this.configVersion = configVersion;
    }

    public Instant getNotifiedAt() {
        return notifiedAt;
    }

    public void setNotifiedAt(Instant notifiedAt) {
        this.notifiedAt = notifiedAt;
    }

    public Instant getRespondedAt() {
        return respondedAt;
    }

    public void setRespondedAt(Instant respondedAt) {
        this.respondedAt = respondedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
