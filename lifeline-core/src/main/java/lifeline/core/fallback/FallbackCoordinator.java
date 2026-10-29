package lifeline.core.fallback;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.factor.Context;
import lifeline.core.factor.FactorContribution;
import lifeline.core.filter.CandidateFilter;
import lifeline.core.filter.FilterResult;
import lifeline.core.log.AllocationEventStream;
import lifeline.core.log.AllocationLogEntry;
import lifeline.core.log.AllocationOutcome;
import lifeline.core.rank.RankedMatch;
import lifeline.core.rank.Ranker;
import lifeline.core.state.DonorState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;

/**
 * Coordinates automated fallback re-ranking when a candidate donor declines or expires.
 * <p>
 * Under SRS Section 7.8 (FB-1 to FB-5) and UF-6:
 * <ul>
 *   <li>On decline or expiry: updates donor state, recomputes ranking over remaining candidates
 *       excluding already-contacted donors, and surfaces the next-best match.</li>
 *   <li>Enforces the configured attempt budget (e.g. 10 contacts per P-24).</li>
 *   <li>Stops on confirmation (ACCEPTED) or exhaustion (EXHAUSTED).</li>
 *   <li>Records each decision into the {@link AllocationEventStream}.</li>
 * </ul>
 */
public final class FallbackCoordinator {

    private static final Logger log = LoggerFactory.getLogger(FallbackCoordinator.class);

    public static final int DEFAULT_ATTEMPT_BUDGET = 10;

    private final CandidateFilter filter;
    private final Ranker ranker;
    private final AllocationEventStream eventStream;

    public FallbackCoordinator(CandidateFilter filter, Ranker ranker, AllocationEventStream eventStream) {
        this.filter = Objects.requireNonNull(filter, "filter cannot be null");
        this.ranker = Objects.requireNonNull(ranker, "ranker cannot be null");
        this.eventStream = Objects.requireNonNull(eventStream, "eventStream cannot be null");
    }

    public FallbackCoordinator(CandidateFilter filter, Ranker ranker) {
        this(filter, ranker, new AllocationEventStream());
    }

    public AllocationEventStream eventStream() {
        return eventStream;
    }

    /**
     * Stateful session for stepping through outreach and fallback transitions for a request.
     */
    public final class Session {
        private final BloodRequest request;
        private final Map<String, DonorSnapshot> pool;
        private final Context ctx;
        private final int attemptBudget;
        private final Set<String> alreadyContacted = new HashSet<>();
        private int currentAttempt = 0;
        private RankedMatch currentMatch;
        private FallbackStatus status;

        private Session(BloodRequest request, Map<String, DonorSnapshot> pool, Context ctx, int attemptBudget) {
            this.request = request;
            this.pool = pool;
            this.ctx = ctx;
            this.attemptBudget = attemptBudget;
        }

        public BloodRequest request() { return request; }
        public int currentAttempt() { return currentAttempt; }
        public RankedMatch currentMatch() { return currentMatch; }
        public FallbackStatus status() { return status; }
        public Set<String> alreadyContacted() { return Collections.unmodifiableSet(alreadyContacted); }

        /**
         * Starts the initial outreach step.
         */
        public FallbackResult initiate() {
            currentAttempt = 1;
            List<DonorSnapshot> candidates = new ArrayList<>(pool.values());
            FilterResult fr = filter.filter(candidates, request);

            if (fr.eligibleDonors().isEmpty()) {
                status = FallbackStatus.EXHAUSTED;
                recordLog(null, 0.0, List.of(), null, AllocationOutcome.EXHAUSTED);
                return FallbackResult.exhausted(currentAttempt, "No eligible donors found in initial pool");
            }

            List<RankedMatch> matches = ranker.rank(fr.eligibleDonors(), request, ctx, 1);
            if (matches.isEmpty()) {
                status = FallbackStatus.EXHAUSTED;
                recordLog(null, 0.0, List.of(), null, AllocationOutcome.EXHAUSTED);
                return FallbackResult.exhausted(currentAttempt, "No candidates ranked in initial pool");
            }

            currentMatch = matches.get(0);
            notifyDonor(currentMatch.donorId());
            status = FallbackStatus.NEXT_MATCH;
            recordLog(currentMatch.donorId(), currentMatch.score(), currentMatch.contributions(),
                currentMatch.explanation(), AllocationOutcome.NOTIFIED);

            return FallbackResult.nextMatch(currentMatch, currentAttempt);
        }

        /**
         * Handles candidate decline and automatically re-ranks remaining candidates.
         */
        public FallbackResult onDeclined() {
            return onFailedResponse(DonorState.DECLINED, AllocationOutcome.DECLINED);
        }

        /**
         * Handles notification expiry without donor response.
         */
        public FallbackResult onExpired() {
            return onFailedResponse(DonorState.EXPIRED, AllocationOutcome.EXPIRED);
        }

        /**
         * Handles candidate acceptance.
         */
        public FallbackResult onAccepted() {
            if (currentMatch == null || status != FallbackStatus.NEXT_MATCH) {
                throw new IllegalStateException("No active candidate match to accept");
            }

            String donorId = currentMatch.donorId();
            DonorSnapshot donor = pool.get(donorId);
            DonorState newState = donor.state().transitionTo(DonorState.ACCEPTED);
            pool.put(donorId, donor.withState(newState));

            recordLog(donorId, currentMatch.score(), currentMatch.contributions(),
                currentMatch.explanation(), AllocationOutcome.ACCEPTED);

            status = FallbackStatus.FULFILLED;
            return FallbackResult.fulfilled(currentMatch, currentAttempt);
        }

        private FallbackResult onFailedResponse(DonorState transitionState, AllocationOutcome outcome) {
            if (currentMatch == null || status != FallbackStatus.NEXT_MATCH) {
                throw new IllegalStateException("No active candidate match to decline/expire");
            }

            String donorId = currentMatch.donorId();
            alreadyContacted.add(donorId);

            // Transition donor state
            DonorSnapshot donor = pool.get(donorId);
            DonorState intermediateState = donor.state().transitionTo(transitionState);
            // Returns to AVAILABLE for future distinct requests per SRS 8.7
            DonorState availableState = intermediateState.transitionTo(DonorState.AVAILABLE);
            pool.put(donorId, donor.withState(availableState));

            recordLog(donorId, currentMatch.score(), currentMatch.contributions(),
                currentMatch.explanation(), outcome);

            currentAttempt++;

            // Budget check
            if (currentAttempt > attemptBudget) {
                status = FallbackStatus.EXHAUSTED;
                recordLog(null, 0.0, List.of(), null, AllocationOutcome.EXHAUSTED);
                return FallbackResult.exhausted(currentAttempt,
                    String.format("Attempt budget exhausted (%d attempts reached)", attemptBudget));
            }

            // Exclude already-contacted candidates
            List<DonorSnapshot> remaining = pool.values().stream()
                .filter(d -> !alreadyContacted.contains(d.id()))
                .toList();

            FilterResult fr = filter.filter(remaining, request);
            if (fr.eligibleDonors().isEmpty()) {
                status = FallbackStatus.EXHAUSTED;
                recordLog(null, 0.0, List.of(), null, AllocationOutcome.EXHAUSTED);
                return FallbackResult.exhausted(currentAttempt, "Remaining candidate pool exhausted");
            }

            List<RankedMatch> matches = ranker.rank(fr.eligibleDonors(), request, ctx, 1);
            if (matches.isEmpty()) {
                status = FallbackStatus.EXHAUSTED;
                recordLog(null, 0.0, List.of(), null, AllocationOutcome.EXHAUSTED);
                return FallbackResult.exhausted(currentAttempt, "No ranked candidates available");
            }

            currentMatch = matches.get(0);
            notifyDonor(currentMatch.donorId());
            status = FallbackStatus.NEXT_MATCH;

            recordLog(currentMatch.donorId(), currentMatch.score(), currentMatch.contributions(),
                currentMatch.explanation(), AllocationOutcome.NOTIFIED);

            return FallbackResult.nextMatch(currentMatch, currentAttempt);
        }

        private void notifyDonor(String donorId) {
            DonorSnapshot d = pool.get(donorId);
            DonorState notifiedState = d.state().transitionTo(DonorState.NOTIFIED);
            pool.put(donorId, d.withState(notifiedState));
        }

        private void recordLog(
            String donorId,
            double score,
            List<FactorContribution> factors,
            lifeline.core.explain.Explanation exp,
            AllocationOutcome outcome
        ) {
            eventStream.append(new AllocationLogEntry(
                request.id(),
                currentAttempt,
                donorId,
                score,
                factors,
                exp,
                outcome,
                Instant.now()
            ));
        }
    }

    /**
     * Creates an interactive session for managing outreach, responses, and automated fallback.
     */
    public Session startSession(
        BloodRequest request,
        Map<String, DonorSnapshot> pool,
        Context ctx,
        int attemptBudget
    ) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(pool, "pool cannot be null");
        Objects.requireNonNull(ctx, "ctx cannot be null");
        if (attemptBudget <= 0) {
            throw new IllegalArgumentException("attemptBudget must be greater than 0, got: " + attemptBudget);
        }
        return new Session(request, pool, ctx, attemptBudget);
    }
}
