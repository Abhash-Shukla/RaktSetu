package lifeline.sim.engine;

import lifeline.core.compatibility.CompatibilityTable;
import lifeline.core.domain.*;
import lifeline.core.inventory.BankInventory;
import lifeline.core.inventory.Tier0InventoryChecker;
import lifeline.core.inventory.Tier0Result;
import lifeline.sim.inventory.InventoryDepletionManager;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.request.RequestGenerator;
import lifeline.sim.response.ResponseModel;
import lifeline.sim.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.random.RandomGenerator;

/**
 * Discrete-event simulation runner.
 * <p>
 * Under docs/EVALUATION_PROTOCOL.md Section 3 and docs/PARAMETERS.md:
 * <ul>
 *   <li>Generates blood request arrivals via Poisson processes per zone.</li>
 *   <li>Invokes candidate selection algorithms via the {@link Matcher} interface.</li>
 *   <li>Enforces busy donors (donors with an outstanding notification are busy).</li>
 *   <li>Enforces cooldown exclusions (90 days male / 120 days female, updated upon accepted donation).</li>
 *   <li>Draws response delays (lognormal) and enforces notification expiry (P-25).</li>
 *   <li>Enforces request attempt budgets (P-24) and clinical deadlines (P-07).</li>
 *   <li>Emits an immutable, chronologically ordered event log.</li>
 * </ul>
 */
public final class SimulationRun {

    private static final Logger log = LoggerFactory.getLogger(SimulationRun.class);

    private enum ActionType {
        REQUEST_ARRIVAL,
        REQUEST_DEADLINE,
        NOTIFICATION_EXPIRED,
        DONOR_RESPONSE
    }

    private record SimAction(
        Instant time,
        long seq,
        ActionType type,
        BloodRequest request,
        String donorId,
        int attemptNumber,
        boolean accepted
    ) implements Comparable<SimAction> {
        @Override
        public int compareTo(SimAction o) {
            int timeCmp = this.time.compareTo(o.time);
            if (timeCmp != 0) {
                return timeCmp;
            }
            return Long.compare(this.seq, o.seq);
        }
    }

    /**
     * Executes a discrete-event simulation run using the supplied world, matcher, and response model.
     *
     * @param world         synthetic simulation world
     * @param matcher       candidate selection strategy under evaluation
     * @param responseModel response dynamics model (M1, M2, or M3)
     * @param requests      sequence of blood requests to process
     * @param seed          random seed for event generation and Bernoulli draws
     * @return summary result and complete event log
     */
    public SimulationResult run(
        World world,
        Matcher matcher,
        ResponseModel responseModel,
        List<BloodRequest> requests,
        long seed
    ) {
        return run(world, matcher, responseModel, requests, seed, false, Tier0InventoryChecker.DEFAULT_RADIUS_KM, Collections.emptyMap());
    }

    /**
     * Executes a discrete-event simulation run with optional Tier-0 inventory resolution.
     *
     * @param world                 synthetic simulation world
     * @param matcher               candidate selection strategy under evaluation
     * @param responseModel         response dynamics model (M1, M2, or M3)
     * @param requests              sequence of blood requests to process
     * @param seed                  random seed for event generation and Bernoulli draws
     * @param tier0Enabled          whether Tier-0 inventory checking is active
     * @param tier0RadiusKm         maximum search radius for nearby blood banks
     * @param populationFrequencies baseline blood group frequencies for depletion prioritization
     * @return summary result and complete event log
     */
    public SimulationResult run(
        World world,
        Matcher matcher,
        ResponseModel responseModel,
        List<BloodRequest> requests,
        long seed,
        boolean tier0Enabled,
        double tier0RadiusKm,
        Map<BloodGroup, Double> populationFrequencies
    ) {
        Objects.requireNonNull(world, "world cannot be null");
        Objects.requireNonNull(matcher, "matcher cannot be null");
        Objects.requireNonNull(responseModel, "responseModel cannot be null");
        Objects.requireNonNull(requests, "requests cannot be null");

        RandomGenerator rng = new SplittableRandom(seed);
        SimulationParameters params = world.parameters();
        ZoneMap zoneMap = world.zoneMap();

        // Mutable simulation state for bank inventories
        Map<String, BankInventory> activeBanks = new LinkedHashMap<>();
        for (BankInventory b : world.bankInventories()) {
            activeBanks.put(b.bankId(), b);
        }
        Tier0InventoryChecker tier0Checker = tier0Enabled ? new Tier0InventoryChecker(zoneMap, tier0RadiusKm) : null;

        // Mutable simulation state per donor
        Map<String, LocalDate> donorLastDonation = new HashMap<>();
        Map<String, List<Instant>> donorRecentContacts = new HashMap<>();
        Map<String, String> busyDonors = new HashMap<>(); // donorId -> active requestId

        for (LatentDonor ld : world.latentDonors().values()) {
            donorLastDonation.put(ld.id(), ld.snapshot().lastDonationDate());
            donorRecentContacts.put(ld.id(), new ArrayList<>());
        }

        // Mutable simulation state per request
        Map<String, Integer> requestUnitsConfirmed = new HashMap<>();
        Map<String, Integer> requestAttempts = new HashMap<>();
        Map<String, Set<String>> requestContacted = new HashMap<>();
        Set<String> resolvedRequests = new HashSet<>();
        Set<String> fulfilledRequests = new HashSet<>();

        List<SimulationEvent> eventLog = new ArrayList<>();
        PriorityQueue<SimAction> eventQueue = new PriorityQueue<>();
        long seqCounter = 0;

        int totalNotificationsSent = 0;
        int totalAccepted = 0;
        int totalDeclined = 0;
        int totalExpired = 0;

        // Initialize event queue with request arrivals and deadlines
        for (BloodRequest req : requests) {
            requestUnitsConfirmed.put(req.id(), 0);
            requestAttempts.put(req.id(), 0);
            requestContacted.put(req.id(), new HashSet<>());

            eventQueue.add(new SimAction(
                req.createdAt(),
                seqCounter++,
                ActionType.REQUEST_ARRIVAL,
                req,
                null,
                0,
                false
            ));

            eventQueue.add(new SimAction(
                req.deadline(),
                seqCounter++,
                ActionType.REQUEST_DEADLINE,
                req,
                null,
                0,
                false
            ));
        }

        Instant simStartTime = requests.isEmpty() ? world.referenceTime() : requests.get(0).createdAt();
        Instant lastEventTime = simStartTime;

        // Discrete-event processing loop
        while (!eventQueue.isEmpty()) {
            SimAction action = eventQueue.poll();
            Instant currentTime = action.time();
            if (currentTime.isAfter(lastEventTime)) {
                lastEventTime = currentTime;
            }

            BloodRequest req = action.request();
            String reqId = req.id();

            switch (action.type()) {
                case REQUEST_ARRIVAL -> {
                    eventLog.add(new SimulationEvent(
                        currentTime,
                        SimulationEventType.REQUEST_ARRIVED,
                        reqId,
                        null,
                        0,
                        String.format("Request arrived for %s units of %s (urgency %s)",
                            req.units(), req.bloodGroup().code(), req.urgency())
                    ));

                    boolean resolvedAtTier0 = false;
                    if (tier0Enabled && tier0Checker != null) {
                        Tier0Result t0Result = tier0Checker.check(req, activeBanks.values());
                        if (t0Result.status() == Tier0Result.Tier0Status.ROUTE_TO_BANK) {
                            BankInventory bank = activeBanks.get(t0Result.bankId());
                            BankInventory depletedBank = InventoryDepletionManager.deplete(
                                bank, req.bloodGroup(), req.units(), populationFrequencies
                            );
                            activeBanks.put(bank.bankId(), depletedBank);

                            resolvedRequests.add(reqId);
                            fulfilledRequests.add(reqId);
                            requestUnitsConfirmed.put(reqId, req.units());

                            eventLog.add(new SimulationEvent(
                                currentTime,
                                SimulationEventType.REQUEST_RESOLVED_TIER0,
                                reqId,
                                t0Result.bankId(),
                                0,
                                t0Result.message()
                            ));
                            eventLog.add(new SimulationEvent(
                                currentTime,
                                SimulationEventType.REQUEST_FULFILLED,
                                reqId,
                                t0Result.bankId(),
                                0,
                                String.format("Request fulfilled at Tier 0 from %s (%d units)", t0Result.bankName(), req.units())
                            ));
                            resolvedAtTier0 = true;
                        }
                    }

                    if (!resolvedAtTier0) {
                        dispatchNextOutreach(
                            req,
                            currentTime,
                            world,
                            matcher,
                            responseModel,
                            params,
                            zoneMap,
                            rng,
                            donorLastDonation,
                            donorRecentContacts,
                            busyDonors,
                            requestUnitsConfirmed,
                            requestAttempts,
                            requestContacted,
                            resolvedRequests,
                            fulfilledRequests,
                            eventLog,
                            eventQueue,
                            seqCounter
                        );
                    }
                }

                case REQUEST_DEADLINE -> {
                    if (!resolvedRequests.contains(reqId)) {
                        resolvedRequests.add(reqId);
                        // Release any busy donor currently attached to this request
                        freeBusyDonorForRequest(reqId, busyDonors);

                        eventLog.add(new SimulationEvent(
                            currentTime,
                            SimulationEventType.REQUEST_EXHAUSTED,
                            reqId,
                            null,
                            requestAttempts.getOrDefault(reqId, 0),
                            "Request deadline exceeded before required units confirmed"
                        ));
                    }
                }

                case NOTIFICATION_EXPIRED -> {
                    if (resolvedRequests.contains(reqId)) {
                        // Already resolved or expired earlier; free donor
                        if (action.donorId() != null) {
                            busyDonors.remove(action.donorId());
                        }
                        break;
                    }

                    if (currentTime.isAfter(req.deadline())) {
                        resolvedRequests.add(reqId);
                        if (action.donorId() != null) {
                            busyDonors.remove(action.donorId());
                        }
                        eventLog.add(new SimulationEvent(
                            currentTime,
                            SimulationEventType.REQUEST_EXHAUSTED,
                            reqId,
                            action.donorId(),
                            action.attemptNumber(),
                            "Deadline passed before response received"
                        ));
                        break;
                    }

                    // Release donor from busy state
                    if (action.donorId() != null) {
                        busyDonors.remove(action.donorId());
                    }
                    totalExpired++;

                    eventLog.add(new SimulationEvent(
                        currentTime,
                        SimulationEventType.NOTIFICATION_EXPIRED,
                        reqId,
                        action.donorId(),
                        action.attemptNumber(),
                        "Donor did not respond within notification window"
                    ));

                    // Step fallback
                    dispatchNextOutreach(
                        req,
                        currentTime,
                        world,
                        matcher,
                        responseModel,
                        params,
                        zoneMap,
                        rng,
                        donorLastDonation,
                        donorRecentContacts,
                        busyDonors,
                        requestUnitsConfirmed,
                        requestAttempts,
                        requestContacted,
                        resolvedRequests,
                        fulfilledRequests,
                        eventLog,
                        eventQueue,
                        seqCounter
                    );
                }

                case DONOR_RESPONSE -> {
                    if (resolvedRequests.contains(reqId)) {
                        if (action.donorId() != null) {
                            busyDonors.remove(action.donorId());
                        }
                        break;
                    }

                    if (currentTime.isAfter(req.deadline())) {
                        resolvedRequests.add(reqId);
                        if (action.donorId() != null) {
                            busyDonors.remove(action.donorId());
                        }
                        eventLog.add(new SimulationEvent(
                            currentTime,
                            SimulationEventType.REQUEST_EXHAUSTED,
                            reqId,
                            action.donorId(),
                            action.attemptNumber(),
                            "Deadline passed before response received"
                        ));
                        break;
                    }

                    // Release donor from busy state
                    if (action.donorId() != null) {
                        busyDonors.remove(action.donorId());
                    }

                    if (action.accepted()) {
                        totalAccepted++;
                        // Update donor's last donation date to simulation current date (enters cooldown)
                        LocalDate donationDate = currentTime.atZone(ZoneOffset.UTC).toLocalDate();
                        donorLastDonation.put(action.donorId(), donationDate);

                        int confirmed = requestUnitsConfirmed.getOrDefault(reqId, 0) + 1;
                        requestUnitsConfirmed.put(reqId, confirmed);

                        eventLog.add(new SimulationEvent(
                            currentTime,
                            SimulationEventType.DONOR_ACCEPTED,
                            reqId,
                            action.donorId(),
                            action.attemptNumber(),
                            String.format("Donor accepted; unit confirmed (%d/%d)", confirmed, req.units())
                        ));

                        if (confirmed >= req.units()) {
                            resolvedRequests.add(reqId);
                            fulfilledRequests.add(reqId);
                            eventLog.add(new SimulationEvent(
                                currentTime,
                                SimulationEventType.REQUEST_FULFILLED,
                                reqId,
                                action.donorId(),
                                action.attemptNumber(),
                                "All requested blood units successfully fulfilled within deadline"
                            ));
                        } else {
                            // More units needed for this request
                            dispatchNextOutreach(
                                req,
                                currentTime,
                                world,
                                matcher,
                                responseModel,
                                params,
                                zoneMap,
                                rng,
                                donorLastDonation,
                                donorRecentContacts,
                                busyDonors,
                                requestUnitsConfirmed,
                                requestAttempts,
                                requestContacted,
                                resolvedRequests,
                                fulfilledRequests,
                                eventLog,
                                eventQueue,
                                seqCounter
                            );
                        }
                    } else {
                        totalDeclined++;
                        eventLog.add(new SimulationEvent(
                            currentTime,
                            SimulationEventType.DONOR_DECLINED,
                            reqId,
                            action.donorId(),
                            action.attemptNumber(),
                            "Donor declined outreach"
                        ));

                        dispatchNextOutreach(
                            req,
                            currentTime,
                            world,
                            matcher,
                            responseModel,
                            params,
                            zoneMap,
                            rng,
                            donorLastDonation,
                            donorRecentContacts,
                            busyDonors,
                            requestUnitsConfirmed,
                            requestAttempts,
                            requestContacted,
                            resolvedRequests,
                            fulfilledRequests,
                            eventLog,
                            eventQueue,
                            seqCounter
                        );
                    }
                }
            }
        }

        // Count total notifications sent
        for (int attempts : requestAttempts.values()) {
            totalNotificationsSent += attempts;
        }

        int totalReqCount = requests.size();
        int fulfilledCount = fulfilledRequests.size();
        int unfulfilledCount = totalReqCount - fulfilledCount;
        Duration totalSpan = Duration.between(simStartTime, lastEventTime);

        return new SimulationResult(
            eventLog,
            totalReqCount,
            fulfilledCount,
            unfulfilledCount,
            totalNotificationsSent,
            totalAccepted,
            totalDeclined,
            totalExpired,
            totalSpan
        );
    }

    /**
     * Executes simulation generating requests automatically over the configured horizon.
     */
    public SimulationResult run(
        World world,
        Matcher matcher,
        ResponseModel responseModel,
        long seed
    ) {
        RequestGenerator requestGen = new RequestGenerator(world.parameters(), loadFrequencies(world));
        List<String> zones = new ArrayList<>(world.zoneMap().centroids().keySet());
        RandomGenerator reqRng = new SplittableRandom(seed ^ 0x9E3779B97F4A7C15L);
        List<BloodRequest> requests = requestGen.generateRequests(zones, world.referenceTime(), reqRng);
        return run(world, matcher, responseModel, requests, seed);
    }

    private void dispatchNextOutreach(
        BloodRequest req,
        Instant currentTime,
        World world,
        Matcher matcher,
        ResponseModel responseModel,
        SimulationParameters params,
        ZoneMap zoneMap,
        RandomGenerator rng,
        Map<String, LocalDate> donorLastDonation,
        Map<String, List<Instant>> donorRecentContacts,
        Map<String, String> busyDonors,
        Map<String, Integer> requestUnitsConfirmed,
        Map<String, Integer> requestAttempts,
        Map<String, Set<String>> requestContacted,
        Set<String> resolvedRequests,
        Set<String> fulfilledRequests,
        List<SimulationEvent> eventLog,
        PriorityQueue<SimAction> eventQueue,
        long seqCounter
    ) {
        String reqId = req.id();
        if (resolvedRequests.contains(reqId)) {
            return;
        }

        if (currentTime.isAfter(req.deadline())) {
            resolvedRequests.add(reqId);
            eventLog.add(new SimulationEvent(
                currentTime,
                SimulationEventType.REQUEST_EXHAUSTED,
                reqId,
                null,
                requestAttempts.getOrDefault(reqId, 0),
                "Request deadline exceeded"
            ));
            return;
        }

        int currentAttempts = requestAttempts.getOrDefault(reqId, 0);
        if (currentAttempts >= params.attemptBudget()) {
            resolvedRequests.add(reqId);
            eventLog.add(new SimulationEvent(
                currentTime,
                SimulationEventType.REQUEST_EXHAUSTED,
                reqId,
                null,
                currentAttempts,
                String.format("Attempt budget exhausted (%d attempts reached)", params.attemptBudget())
            ));
            return;
        }

        LocalDate currentDate = currentTime.atZone(ZoneOffset.UTC).toLocalDate();
        Set<String> alreadyContacted = requestContacted.getOrDefault(reqId, Collections.emptySet());

        // Hard Candidate Filter: Compatibility + Availability + Outside Cooldown + Not Busy + Not Already Contacted
        List<DonorSnapshot> eligiblePool = new ArrayList<>();
        for (LatentDonor ld : world.latentDonors().values()) {
            String donorId = ld.id();

            // 1. Not already contacted for this request
            if (alreadyContacted.contains(donorId)) {
                continue;
            }

            // 2. Not busy with another request
            if (busyDonors.containsKey(donorId)) {
                continue;
            }

            // 3. ABO/Rh Compatibility (hard filter)
            if (!CompatibilityTable.isCompatible(ld.bloodGroup(), req.bloodGroup())) {
                continue;
            }

            // 4. Availability
            if (!ld.snapshot().availability()) {
                continue;
            }

            // 5. Cooldown check (90 days male / 120 days female, P-10)
            LocalDate lastDonation = donorLastDonation.getOrDefault(donorId, ld.snapshot().lastDonationDate());
            long daysSinceDonation = ChronoUnit.DAYS.between(lastDonation, currentDate);
            int cooldownDays = (ld.snapshot().sex() == Sex.MALE) ? params.cooldownDaysMale() : params.cooldownDaysFemale();
            if (daysSinceDonation < cooldownDays) {
                continue;
            }

            eligiblePool.add(ld.snapshot());
        }

        if (eligiblePool.isEmpty()) {
            resolvedRequests.add(reqId);
            eventLog.add(new SimulationEvent(
                currentTime,
                SimulationEventType.REQUEST_EXHAUSTED,
                reqId,
                null,
                currentAttempts,
                "Eligible candidate pool exhausted"
            ));
            return;
        }

        Optional<String> selectedIdOpt = matcher.selectNextCandidate(req, eligiblePool, alreadyContacted, currentTime);
        if (selectedIdOpt.isEmpty()) {
            resolvedRequests.add(reqId);
            eventLog.add(new SimulationEvent(
                currentTime,
                SimulationEventType.REQUEST_EXHAUSTED,
                reqId,
                null,
                currentAttempts,
                "Matcher selected no candidate"
            ));
            return;
        }

        String selectedId = selectedIdOpt.get();
        LatentDonor selectedDonor = world.latentDonors().get(selectedId);
        if (selectedDonor == null) {
            resolvedRequests.add(reqId);
            eventLog.add(new SimulationEvent(
                currentTime,
                SimulationEventType.REQUEST_EXHAUSTED,
                reqId,
                selectedId,
                currentAttempts,
                "Selected candidate not found in world"
            ));
            return;
        }

        // Advance attempt count and mark busy
        int newAttempt = currentAttempts + 1;
        requestAttempts.put(reqId, newAttempt);
        requestContacted.computeIfAbsent(reqId, k -> new HashSet<>()).add(selectedId);
        busyDonors.put(selectedId, reqId);

        // Record contact timestamp for M2 fatigue tracking
        donorRecentContacts.computeIfAbsent(selectedId, k -> new ArrayList<>()).add(currentTime);

        eventLog.add(new SimulationEvent(
            currentTime,
            SimulationEventType.DONOR_NOTIFIED,
            reqId,
            selectedId,
            newAttempt,
            String.format("Notification dispatched to donor %s (attempt %d)", selectedId, newAttempt)
        ));

        // Sample response delay (lognormal, P-17)
        Duration delay = responseModel.drawResponseDelay(selectedDonor, rng);

        // Check urgency notification expiry threshold (P-25)
        long expiryMinutes = switch (req.urgency()) {
            case CRITICAL -> params.expiryMinutesCritical();
            case URGENT -> params.expiryMinutesUrgent();
            case ROUTINE -> params.expiryMinutesRoutine();
        };

        if (delay.toMinutes() > expiryMinutes) {
            // Notification expires before donor responds
            Instant expiryTime = currentTime.plus(Duration.ofMinutes(expiryMinutes));
            eventQueue.add(new SimAction(
                expiryTime,
                seqCounter,
                ActionType.NOTIFICATION_EXPIRED,
                req,
                selectedId,
                newAttempt,
                false
            ));
        } else {
            // Donor responds within expiry window
            Instant responseTime = currentTime.plus(delay);

            // Compute distance from donor location (fresh or home zone)
            String donorZone;
            if (selectedDonor.snapshot().currentLocation().isPresent()) {
                CurrentLocation loc = selectedDonor.snapshot().currentLocation().get();
                Duration age = Duration.between(loc.confirmedAt(), currentTime);
                boolean isFresh = !age.isNegative() && age.compareTo(Duration.ofHours(params.freshnessWindowHours())) <= 0;
                donorZone = isFresh ? loc.zone() : selectedDonor.homeZone();
            } else {
                donorZone = selectedDonor.homeZone();
            }
            double distanceKm = zoneMap.distanceKm(donorZone, req.zone());

            // Count contacts in previous 30 days
            Instant thirtyDaysAgo = responseTime.minus(Duration.ofDays(30));
            int contactsLast30Days = 0;
            List<Instant> pastContacts = donorRecentContacts.get(selectedId);
            if (pastContacts != null) {
                for (Instant c : pastContacts) {
                    if (!c.isBefore(thirtyDaysAgo) && !c.isAfter(responseTime)) {
                        contactsLast30Days++;
                    }
                }
            }

            double timeOfDayHour = responseTime.atZone(ZoneOffset.UTC).getHour()
                + responseTime.atZone(ZoneOffset.UTC).getMinute() / 60.0;

            // Draw Bernoulli response from model acceptance probability
            double p = responseModel.trueAcceptanceProbability(
                selectedDonor,
                req,
                distanceKm,
                contactsLast30Days,
                timeOfDayHour
            );
            boolean accepted = rng.nextDouble() < p;

            eventQueue.add(new SimAction(
                responseTime,
                seqCounter,
                ActionType.DONOR_RESPONSE,
                req,
                selectedId,
                newAttempt,
                accepted
            ));
        }
    }

    private void freeBusyDonorForRequest(String reqId, Map<String, String> busyDonors) {
        Iterator<Map.Entry<String, String>> it = busyDonors.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, String> entry = it.next();
            if (reqId.equals(entry.getValue())) {
                it.remove();
            }
        }
    }

    private Map<BloodGroup, Double> loadFrequencies(World world) {
        Map<BloodGroup, Integer> counts = new EnumMap<>(BloodGroup.class);
        int total = 0;
        for (LatentDonor d : world.latentDonors().values()) {
            counts.merge(d.bloodGroup(), 1, Integer::sum);
            total++;
        }
        Map<BloodGroup, Double> freqs = new EnumMap<>(BloodGroup.class);
        for (Map.Entry<BloodGroup, Integer> e : counts.entrySet()) {
            freqs.put(e.getKey(), total == 0 ? 0.0 : (double) e.getValue() / total);
        }
        return freqs;
    }
}
