package lifeline.core.fallback;

import lifeline.core.config.CooldownConfig;
import lifeline.core.config.WeightProfile;
import lifeline.core.domain.*;
import lifeline.core.factor.Context;
import lifeline.core.filter.CandidateFilter;
import lifeline.core.filter.ExclusionReason;
import lifeline.core.filter.FilterResult;
import lifeline.core.log.AllocationEventStream;
import lifeline.core.log.AllocationLogEntry;
import lifeline.core.log.AllocationOutcome;
import lifeline.core.rank.Ranker;
import lifeline.core.state.DonorState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

class FallbackCoordinatorTest {

    private FallbackCoordinator coordinator;
    private AllocationEventStream eventStream;
    private CandidateFilter filter;
    private Ranker ranker;
    private ZoneMap zoneMap;
    private Instant now;
    private Context ctx;

    @BeforeEach
    void setUp() {
        filter = new CandidateFilter(CooldownConfig.defaults());
        ranker = new Ranker();
        eventStream = new AllocationEventStream();
        coordinator = new FallbackCoordinator(filter, ranker, eventStream);

        now = Instant.parse("2026-10-15T12:00:00Z");

        zoneMap = new ZoneMap(
            Map.of(
                "Z1", new GeoCoordinate(18.9220, 72.8347), // Origin
                "Z2", new GeoCoordinate(19.0176, 72.8561), // ~10km away
                "Z3", new GeoCoordinate(19.1663, 72.8526), // ~25km away
                "Z4", new GeoCoordinate(19.3000, 72.8500)  // ~40km away
            ),
            1.3
        );

        ctx = new Context(
            zoneMap,
            Duration.ofHours(24),
            now,
            PopulationFrequencyTable.defaultIndianPopulation(),
            10.0,
            1.0,
            2.0,
            new WeightProfile(0.65, 0.35, 0.0),
            Context.DEFAULT_ACTIVE_FACTORS
        );
    }

    private DonorSnapshot createAvailableDonor(String id, BloodGroup group, String zone) {
        return new DonorSnapshot(
            id,
            group,
            Sex.MALE,
            zone,
            LocalDate.of(2026, 1, 1),
            DonorState.AVAILABLE,
            Optional.empty(),
            HistoryStats.empty()
        );
    }

    @Test
    @DisplayName("Fallback order: D1 declines -> surfaces D2; D2 expires -> surfaces D3; D3 accepts -> FULFILLED")
    void fallbackOrderAndAcceptance() {
        BloodRequest request = new BloodRequest(
            "REQ-FB-1",
            BloodGroup.B_POS,
            1,
            Urgency.URGENT,
            "Z1",
            now,
            now.plus(Duration.ofHours(6))
        );

        // Donors ranked by distance from Z1: D1 in Z1 (best), D2 in Z2 (2nd), D3 in Z3 (3rd)
        Map<String, DonorSnapshot> pool = new HashMap<>();
        pool.put("D1", createAvailableDonor("D1", BloodGroup.B_POS, "Z1"));
        pool.put("D2", createAvailableDonor("D2", BloodGroup.B_POS, "Z2"));
        pool.put("D3", createAvailableDonor("D3", BloodGroup.B_POS, "Z3"));

        FallbackCoordinator.Session session = coordinator.startSession(request, pool, ctx, 5);

        // Step 1: Initiate outreach -> D1 surfaced
        FallbackResult r1 = session.initiate();
        assertThat(r1.status()).isEqualTo(FallbackStatus.NEXT_MATCH);
        assertThat(r1.currentMatch().donorId()).isEqualTo("D1");
        assertThat(r1.attemptNumber()).isEqualTo(1);
        assertThat(pool.get("D1").state()).isEqualTo(DonorState.NOTIFIED);

        // Step 2: D1 declines -> D2 surfaced
        FallbackResult r2 = session.onDeclined();
        assertThat(r2.status()).isEqualTo(FallbackStatus.NEXT_MATCH);
        assertThat(r2.currentMatch().donorId()).isEqualTo("D2");
        assertThat(r2.attemptNumber()).isEqualTo(2);
        assertThat(session.alreadyContacted()).contains("D1");
        assertThat(pool.get("D2").state()).isEqualTo(DonorState.NOTIFIED);

        // Step 3: D2 expires -> D3 surfaced
        FallbackResult r3 = session.onExpired();
        assertThat(r3.status()).isEqualTo(FallbackStatus.NEXT_MATCH);
        assertThat(r3.currentMatch().donorId()).isEqualTo("D3");
        assertThat(r3.attemptNumber()).isEqualTo(3);
        assertThat(session.alreadyContacted()).contains("D1", "D2");
        assertThat(pool.get("D3").state()).isEqualTo(DonorState.NOTIFIED);

        // Step 4: D3 accepts -> FULFILLED
        FallbackResult r4 = session.onAccepted();
        assertThat(r4.status()).isEqualTo(FallbackStatus.FULFILLED);
        assertThat(r4.currentMatch().donorId()).isEqualTo("D3");
        assertThat(pool.get("D3").state()).isEqualTo(DonorState.ACCEPTED);

        // Verify allocation event stream trace
        List<AllocationLogEntry> logs = eventStream.entriesForRequest("REQ-FB-1");
        assertThat(logs).extracting(AllocationLogEntry::outcome)
            .containsExactly(
                AllocationOutcome.NOTIFIED, // D1 notified
                AllocationOutcome.DECLINED, // D1 declined
                AllocationOutcome.NOTIFIED, // D2 notified
                AllocationOutcome.EXPIRED,  // D2 expired
                AllocationOutcome.NOTIFIED, // D3 notified
                AllocationOutcome.ACCEPTED  // D3 accepted
            );
    }

    @Test
    @DisplayName("Exhaustion: stops and returns EXHAUSTED when attempt budget is exceeded")
    void exhaustionByAttemptBudget() {
        BloodRequest request = new BloodRequest(
            "REQ-BUDGET",
            BloodGroup.B_POS,
            1,
            Urgency.URGENT,
            "Z1",
            now,
            now.plus(Duration.ofHours(6))
        );

        Map<String, DonorSnapshot> pool = new HashMap<>();
        pool.put("D1", createAvailableDonor("D1", BloodGroup.B_POS, "Z1"));
        pool.put("D2", createAvailableDonor("D2", BloodGroup.B_POS, "Z2"));
        pool.put("D3", createAvailableDonor("D3", BloodGroup.B_POS, "Z3"));

        // Attempt budget of exactly 2
        FallbackCoordinator.Session session = coordinator.startSession(request, pool, ctx, 2);

        session.initiate(); // Attempt 1: D1 notified
        session.onDeclined(); // Attempt 2: D2 notified
        FallbackResult r3 = session.onDeclined(); // Attempt 3 > Budget (2) -> EXHAUSTED

        assertThat(r3.status()).isEqualTo(FallbackStatus.EXHAUSTED);
        assertThat(r3.isExhausted()).isTrue();
        assertThat(r3.message()).contains("Attempt budget exhausted");

        List<AllocationLogEntry> logs = eventStream.entriesForRequest("REQ-BUDGET");
        assertThat(logs.get(logs.size() - 1).outcome()).isEqualTo(AllocationOutcome.EXHAUSTED);
    }

    @Test
    @DisplayName("Exhaustion: stops and returns EXHAUSTED when candidate pool is depleted")
    void exhaustionByPoolDepletion() {
        BloodRequest request = new BloodRequest(
            "REQ-DEPLETE",
            BloodGroup.B_POS,
            1,
            Urgency.URGENT,
            "Z1",
            now,
            now.plus(Duration.ofHours(6))
        );

        // Only 1 donor in pool
        Map<String, DonorSnapshot> pool = new HashMap<>();
        pool.put("D1", createAvailableDonor("D1", BloodGroup.B_POS, "Z1"));

        FallbackCoordinator.Session session = coordinator.startSession(request, pool, ctx, 5);
        session.initiate(); // D1 notified
        FallbackResult r2 = session.onDeclined(); // D1 declined, pool empty

        assertThat(r2.status()).isEqualTo(FallbackStatus.EXHAUSTED);
        assertThat(r2.message()).contains("exhausted");
    }

    @Test
    @DisplayName("No double notification: a donor with an outstanding NOTIFIED state is excluded from matching")
    void noDoubleNotificationForBusyDonor() {
        BloodRequest reqA = new BloodRequest("REQ-A", BloodGroup.B_POS, 1, Urgency.URGENT, "Z1", now, now.plusSeconds(3600));
        BloodRequest reqB = new BloodRequest("REQ-B", BloodGroup.B_POS, 1, Urgency.URGENT, "Z1", now, now.plusSeconds(3600));

        // Donor D1 is currently in NOTIFIED state for reqA
        DonorSnapshot busyDonor = new DonorSnapshot(
            "D-BUSY",
            BloodGroup.B_POS,
            Sex.MALE,
            "Z1",
            LocalDate.of(2026, 1, 1),
            DonorState.NOTIFIED,
            Optional.empty(),
            HistoryStats.empty()
        );

        FilterResult result = filter.filter(List.of(busyDonor), reqB);

        assertThat(result.isEligible("D-BUSY")).isFalse();
        assertThat(result.findExclusion("D-BUSY").orElseThrow().reason())
            .isEqualTo(ExclusionReason.OUTSTANDING_NOTIFICATION);
    }
}
