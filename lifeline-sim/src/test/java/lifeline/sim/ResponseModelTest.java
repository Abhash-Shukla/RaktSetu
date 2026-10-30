package lifeline.sim;

import lifeline.core.domain.*;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.response.M1BaselineLogisticModel;
import lifeline.sim.response.M2DynamicFatigueModel;
import lifeline.sim.response.M3MisspecifiedModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.SplittableRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ResponseModelTest {

    private final SimulationParameters params = SimulationParameters.loadDefault();

    private LatentDonor createTestDonor(double willingness, double reliability, double travelToleranceKm) {
        DonorSnapshot snapshot = new DonorSnapshot(
            "TEST-01",
            BloodGroup.O_POS,
            Sex.MALE,
            "Z01",
            LocalDate.of(2026, 1, 1),
            lifeline.core.state.DonorState.AVAILABLE,
            Optional.empty(),
            HistoryStats.empty()
        );
        return new LatentDonor(
            snapshot,
            willingness,
            reliability,
            30.0,
            travelToleranceKm,
            0.80,
            0.0
        );
    }

    private BloodRequest createTestRequest(Urgency urgency) {
        Instant now = Instant.parse("2026-10-15T12:00:00Z");
        return new BloodRequest(
            "REQ-TEST-1",
            BloodGroup.O_POS,
            1,
            urgency,
            "Z01",
            now,
            now.plus(Duration.ofHours(2))
        );
    }

    @Test
    @DisplayName("M1: Distance decay reduces acceptance probability and urgency boosts it")
    void m1DistanceDecayAndUrgencyBonus() {
        M1BaselineLogisticModel m1 = new M1BaselineLogisticModel(params);
        LatentDonor donor = createTestDonor(0.40, 0.90, 10.0);

        BloodRequest routineReq = createTestRequest(Urgency.ROUTINE);
        BloodRequest criticalReq = createTestRequest(Urgency.CRITICAL);

        double pAtOrigin = m1.trueAcceptanceProbability(donor, routineReq, 0.0, 0, 14.0);
        double pAtTolerance = m1.trueAcceptanceProbability(donor, routineReq, 10.0, 0, 14.0);
        double pFarAway = m1.trueAcceptanceProbability(donor, routineReq, 30.0, 0, 14.0);

        // Distance decay: acceptance at tolerance distance should be half of origin
        assertThat(pAtTolerance).isCloseTo(pAtOrigin * 0.5, within(0.01));
        assertThat(pFarAway).isLessThan(pAtTolerance);

        // Urgency bonus: critical urgency lifts relative acceptance by +15% (P-21)
        double pCriticalAtOrigin = m1.trueAcceptanceProbability(donor, criticalReq, 0.0, 0, 14.0);
        assertThat(pCriticalAtOrigin).isCloseTo(pAtOrigin * 1.15, within(0.01));
    }

    @Test
    @DisplayName("M2: Donor fatigue reduces acceptance with recent contacts, and time-of-day modulates response")
    void m2FatigueAndTimeOfDay() {
        M2DynamicFatigueModel m2 = new M2DynamicFatigueModel(params);
        LatentDonor donor = createTestDonor(0.40, 0.90, 10.0);
        BloodRequest req = createTestRequest(Urgency.ROUTINE);

        // At 14:00 (afternoon peak)
        double p0Contacts = m2.trueAcceptanceProbability(donor, req, 0.0, 0, 14.0);
        double p1Contact = m2.trueAcceptanceProbability(donor, req, 0.0, 1, 14.0);
        double p3Contacts = m2.trueAcceptanceProbability(donor, req, 0.0, 3, 14.0);

        // Fatigue: 0.80 per contact in previous 30 days (P-19)
        assertThat(p1Contact).isCloseTo(p0Contacts * 0.80, within(0.01));
        assertThat(p3Contacts).isCloseTo(p0Contacts * Math.pow(0.80, 3), within(0.01));

        // Time-of-day effect (P-20: +/- 20% relative)
        // 14:00 is daytime peak; 02:00 is night trough
        double pNight = m2.trueAcceptanceProbability(donor, req, 0.0, 0, 2.0);
        assertThat(pNight).isLessThan(p0Contacts);
        assertThat(pNight / p0Contacts).isCloseTo(0.80 / 1.20, within(0.05));
    }

    @Test
    @DisplayName("M3: Mis-specified model has zero distance effect and reflects hidden zone affinity")
    void m3MisspecifiedModel() {
        M3MisspecifiedModel m3 = new M3MisspecifiedModel(params);

        LatentDonor donorWithAffinity = new LatentDonor(
            createTestDonor(0.40, 0.90, 10.0).snapshot(),
            0.40,
            0.90,
            30.0,
            10.0,
            0.80,
            0.50 // +0.5 logit affinity
        );

        LatentDonor donorNeutral = new LatentDonor(
            createTestDonor(0.40, 0.90, 10.0).snapshot(),
            0.40,
            0.90,
            30.0,
            10.0,
            0.80,
            0.0 // neutral
        );

        BloodRequest req = createTestRequest(Urgency.ROUTINE);

        double pNear = m3.trueAcceptanceProbability(donorNeutral, req, 0.0, 0, 14.0);
        double pFar = m3.trueAcceptanceProbability(donorNeutral, req, 50.0, 0, 14.0);

        // Distance effect is zero in M3 (P-22)
        assertThat(pNear).isCloseTo(pFar, within(1e-6));

        // Hidden zone affinity lifts acceptance in logit space
        double pAffinity = m3.trueAcceptanceProbability(donorWithAffinity, req, 0.0, 0, 14.0);
        assertThat(pAffinity).isGreaterThan(pNear);
    }

    @Test
    @DisplayName("Response delay sampling draws valid lognormal durations around median 30 min")
    void responseDelaySampling() {
        M1BaselineLogisticModel m1 = new M1BaselineLogisticModel(params);
        LatentDonor donor = createTestDonor(0.40, 0.90, 10.0);
        SplittableRandom rng = new SplittableRandom(42L);

        double sum = 0.0;
        int n = 1000;
        for (int i = 0; i < n; i++) {
            Duration delay = m1.drawResponseDelay(donor, rng);
            assertThat(delay.toMinutes()).isGreaterThanOrEqualTo(1);
            sum += delay.toMinutes();
        }

        double mean = sum / n;
        // Lognormal with median 30 min and sigma 1.0 has mean = 30 * exp(0.5) ~ 49.4 min
        assertThat(mean).isBetween(35.0, 70.0);
    }
}
