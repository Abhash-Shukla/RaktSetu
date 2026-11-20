package lifeline.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lifeline.api.dto.BloodRequestResponseDto;
import lifeline.api.dto.NotificationResponseDto;
import lifeline.api.dto.RankedMatchDto;
import lifeline.api.dto.Tier0CheckDto;
import lifeline.api.model.*;
import lifeline.api.repository.*;
import lifeline.core.compatibility.CompatibilityTable;
import lifeline.core.config.EngineConfig;
import lifeline.core.config.WeightProfile;
import lifeline.core.domain.*;
import lifeline.core.explain.Explanation;
import lifeline.core.factor.*;
import lifeline.core.filter.CandidateFilter;
import lifeline.core.filter.FilterResult;
import lifeline.core.inventory.BankInventory;
import lifeline.core.inventory.Tier0InventoryChecker;
import lifeline.core.inventory.Tier0Result;
import lifeline.core.rank.RankedMatch;
import lifeline.core.rank.Ranker;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MatchingService {

    private final BloodRequestRepository bloodRequestRepository;
    private final DonorProfileRepository donorProfileRepository;
    private final BankRepository bankRepository;
    private final BankInventoryRepository bankInventoryRepository;
    private final MatchLogRepository matchLogRepository;
    private final EngineConfigVersionRepository engineConfigVersionRepository;
    private final AuditService auditService;
    private final BloodRequestService bloodRequestService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MatchingService(
        BloodRequestRepository bloodRequestRepository,
        DonorProfileRepository donorProfileRepository,
        BankRepository bankRepository,
        BankInventoryRepository bankInventoryRepository,
        MatchLogRepository matchLogRepository,
        EngineConfigVersionRepository engineConfigVersionRepository,
        AuditService auditService,
        BloodRequestService bloodRequestService
    ) {
        this.bloodRequestRepository = bloodRequestRepository;
        this.donorProfileRepository = donorProfileRepository;
        this.bankRepository = bankRepository;
        this.bankInventoryRepository = bankInventoryRepository;
        this.matchLogRepository = matchLogRepository;
        this.engineConfigVersionRepository = engineConfigVersionRepository;
        this.auditService = auditService;
        this.bloodRequestService = bloodRequestService;
    }

    /**
     * Evaluates Tier-0 hospital/bank inventory stock within configured radius.
     * Hard rule: Public intake requests in PENDING_REVIEW cannot trigger Tier-0 checks.
     */
    @Transactional(readOnly = true)
    public Tier0CheckDto checkTier0(Long requestId) {
        BloodRequestEntity requestEntity = bloodRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Blood request not found with id: " + requestId));

        if (requestEntity.getStatus() == RequestStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Request is in PENDING_REVIEW; staff review and approval required before matching.");
        }

        BloodRequest coreRequest = toCoreRequest(requestEntity);
        ZoneMap zoneMap = buildZoneMap();
        List<BankInventory> coreBankInventories = loadCoreBankInventories();

        Tier0InventoryChecker checker = new Tier0InventoryChecker(zoneMap, Tier0InventoryChecker.DEFAULT_RADIUS_KM);
        Tier0Result result = checker.check(coreRequest, coreBankInventories);

        if (result.status() == Tier0Result.Tier0Status.ROUTE_TO_BANK) {
            BankEntity bankEntity = bankRepository.findAll().stream()
                .filter(b -> b.getId().toString().equals(result.bankId()) || b.getName().equals(result.bankName()))
                .findFirst()
                .orElse(null);

            Long bankId = bankEntity != null ? bankEntity.getId() : null;
            String bankName = bankEntity != null ? bankEntity.getName() : result.bankName();
            String bankZone = bankEntity != null ? bankEntity.getZone() : result.bankZone();

            return new Tier0CheckDto(
                true,
                bankId,
                bankName,
                bankZone,
                result.compatibleUnitsFound(),
                String.format("Tier-0 route: Found %d compatible units at %s (approx %.1f km away).",
                    result.compatibleUnitsFound(), bankName, result.distanceKm())
            );
        } else {
            return new Tier0CheckDto(
                false,
                null,
                null,
                null,
                0,
                "No nearby blood bank has sufficient compatible stock within 25.0 km. Falling through to Tier-1 donor matching."
            );
        }
    }

    /**
     * Fulfills request via Tier-0 inventory allocation.
     * Uses JPA @Version optimistic locking to protect bank inventory units.
     */
    @Transactional
    public BloodRequestResponseDto fulfillViaTier0(Long requestId, Long bankId, String staffUsername, String ipAddress) {
        BloodRequestEntity request = bloodRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Blood request not found: " + requestId));

        if (request.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("Cannot fulfill request in status: " + request.getStatus());
        }

        BankEntity bank = bankRepository.findById(bankId)
            .orElseThrow(() -> new IllegalArgumentException("Bank not found: " + bankId));

        int neededUnits = request.getUnitsRequested() - request.getUnitsFulfilled();
        if (neededUnits <= 0) {
            throw new IllegalStateException("Request already fulfilled.");
        }

        BloodGroup requestedGroup = BloodGroup.valueOf(request.getBloodGroup());
        List<BankInventoryEntity> inventories = bankInventoryRepository.findByBankId(bankId);

        // Preference order: exact match first, then compatible groups by Indian population frequency
        List<BloodGroup> allocationOrder = new ArrayList<>();
        allocationOrder.add(requestedGroup);
        for (BloodGroup candidate : BloodGroup.values()) {
            if (candidate != requestedGroup && CompatibilityTable.isCompatible(candidate, requestedGroup)) {
                allocationOrder.add(candidate);
            }
        }

        int remaining = neededUnits;
        for (BloodGroup group : allocationOrder) {
            if (remaining <= 0) break;
            for (BankInventoryEntity inv : inventories) {
                if (inv.getBloodGroup().equals(group.name()) && inv.getUnitsAvailable() > 0) {
                    int consume = Math.min(remaining, inv.getUnitsAvailable());
                    inv.setUnitsAvailable(inv.getUnitsAvailable() - consume);
                    bankInventoryRepository.save(inv);
                    remaining -= consume;
                    if (remaining <= 0) break;
                }
            }
        }

        if (remaining > 0) {
            throw new IllegalStateException("Insufficient compatible inventory at selected bank. Remaining needed: " + remaining);
        }

        request.setUnitsFulfilled(request.getUnitsRequested());
        request.setStatus(RequestStatus.RESOLVED_TIER0);
        request = bloodRequestRepository.save(request);

        auditService.log(staffUsername, "BANK_STAFF", "TIER0_FULFILLMENT", "BLOOD_REQUEST",
            request.getId().toString(), String.format("Fulfilled %d units via Tier-0 Bank #%d (%s)", neededUnits, bankId, bank.getName()), ipAddress);

        return bloodRequestService.mapToDto(request);
    }

    /**
     * Ranks candidate donors using greedy Top-N multi-factor scoring (locked-spec S6 baseline, S7 optional).
     * Hard rule: Public intake requests in PENDING_REVIEW cannot be matched.
     */
    @Transactional(readOnly = true)
    public List<RankedMatchDto> rankDonors(Long requestId, Integer topN, Boolean includeResponsiveness) {
        BloodRequestEntity requestEntity = bloodRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Blood request not found: " + requestId));

        if (requestEntity.getStatus() == RequestStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Request is in PENDING_REVIEW; staff review and approval required before matching.");
        }
        if (requestEntity.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("Cannot match request in status: " + requestEntity.getStatus());
        }

        BloodRequest coreRequest = toCoreRequest(requestEntity);
        ZoneMap zoneMap = buildZoneMap();

        List<DonorProfileEntity> donorEntities = donorProfileRepository.findAll();
        Map<Long, DonorProfileEntity> donorMap = donorEntities.stream()
            .collect(Collectors.toMap(DonorProfileEntity::getId, d -> d));

        List<DonorSnapshot> snapshots = donorEntities.stream()
            .map(this::toDonorSnapshot)
            .toList();

        CandidateFilter filter = new CandidateFilter(EngineConfig.defaults());
        FilterResult filterResult = filter.filter(snapshots, coreRequest);

        // Active factors: Locked-spec S6 (Proximity + Rarity) by default. Responsiveness is an optional S7 toggle.
        Set<String> activeFactors = new HashSet<>(Context.DEFAULT_ACTIVE_FACTORS);
        if (Boolean.TRUE.equals(includeResponsiveness)) {
            activeFactors.add(ResponsivenessFactor.NAME);
        }

        EngineConfig config = EngineConfig.defaults();
        WeightProfile weights = config.urgencyProfiles().get(coreRequest.urgency());
        Duration freshness = Duration.ofHours(config.geography().locationFreshnessHours());

        Context context = new Context(
            zoneMap,
            freshness,
            coreRequest.createdAt(),
            PopulationFrequencyTable.defaultIndianPopulation(),
            Context.DEFAULT_HALF_DISTANCE_KM,
            Context.DEFAULT_PRIOR_ALPHA,
            Context.DEFAULT_PRIOR_BETA,
            weights,
            activeFactors
        );

        int n = (topN != null && topN > 0) ? topN : config.selection().defaultTopN();
        Ranker ranker = new Ranker();
        List<RankedMatch> matches = ranker.rank(filterResult.eligibleDonors(), coreRequest, context, n);

        List<RankedMatchDto> results = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            RankedMatch match = matches.get(i);
            Explanation explanation = match.explanation();
            Map<String, Double> factorMap = match.contributions().stream()
                .collect(Collectors.toMap(
                    FactorContribution::factorName,
                    FactorContribution::contribution
                ));

            DonorProfileEntity donorEntity = donorMap.get(Long.parseLong(match.donorId()));

            results.add(new RankedMatchDto(
                i + 1,
                Long.parseLong(match.donorId()),
                donorEntity != null ? donorEntity.getBloodGroup() : "UNKNOWN",
                donorEntity != null ? donorEntity.getHomeZone() : "UNKNOWN",
                match.score(),
                explanation.summary(),
                explanation.topContributingFactor(),
                factorMap
            ));
        }

        return results;
    }

    /**
     * Contacts a candidate donor from ranked list.
     * Creates an immutable MatchLog entry and updates donor profile contacts.
     */
    @Transactional
    public NotificationResponseDto notifyDonor(
        Long requestId,
        Long donorId,
        String staffUsername,
        String ipAddress
    ) {
        BloodRequestEntity request = bloodRequestRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Blood request not found: " + requestId));

        if (request.getStatus() != RequestStatus.OPEN) {
            throw new IllegalStateException("Cannot notify donor for request in status: " + request.getStatus());
        }

        DonorProfileEntity donor = donorProfileRepository.findById(donorId)
            .orElseThrow(() -> new IllegalArgumentException("Donor not found: " + donorId));

        if (donor.isPaused() || !donor.isAvailable()) {
            throw new IllegalStateException("Donor is paused or unavailable.");
        }

        // Rank to get exact factor contribution and explanation
        List<RankedMatchDto> ranked = rankDonors(requestId, 50, false);
        RankedMatchDto match = ranked.stream()
            .filter(m -> m.donorId().equals(donorId))
            .findFirst()
            .orElse(null);

        int rankPos = match != null ? match.rankPosition() : 1;
        double score = match != null ? match.totalScore() : 1.0;
        String explanationText = match != null ? match.plainLanguageExplanation() : "Direct staff outreach.";
        String reasonCode = match != null ? match.reasonCode() : "STAFF_DIRECT";
        String breakdownJson = match != null ? serializeJson(match.factorContributions()) : "{}";

        long stepCount = matchLogRepository.countByRequestId(requestId);

        MatchLogEntity logEntity = new MatchLogEntity();
        logEntity.setRequest(request);
        logEntity.setDonor(donor);
        logEntity.setStepNumber((int) stepCount + 1);
        logEntity.setRankPosition(rankPos);
        logEntity.setTotalScore(score);
        logEntity.setFactorBreakdown(breakdownJson);
        logEntity.setExplanationText(explanationText);
        logEntity.setReasonCode(reasonCode);
        logEntity.setOutcome(MatchOutcome.NOTIFIED);
        logEntity.setNotifiedAt(Instant.now());

        // Attach active config version if present
        engineConfigVersionRepository.findByActiveTrue().ifPresent(logEntity::setConfigVersion);

        matchLogRepository.save(logEntity);

        // Update donor profile stats atomically (with optimistic locking)
        donor.setTotalContacts(donor.getTotalContacts() + 1);
        donorProfileRepository.save(donor);

        auditService.log(staffUsername, "BANK_STAFF", "DONOR_NOTIFIED", "MATCH_LOG",
            logEntity.getId().toString(), String.format("Notified donor #%d for request #%d", donorId, requestId), ipAddress);

        return new NotificationResponseDto(
            logEntity.getId(),
            request.getId(),
            request.getPatientReference(),
            request.getBloodGroup(),
            request.getUrgency(),
            request.getZone(),
            request.getDeadline(),
            logEntity.getOutcome().name(),
            logEntity.getNotifiedAt()
        );
    }

    /**
     * Records a donor's response (ACCEPTED, DECLINED, EXPIRED, CANCELLED).
     * Atomic donor reservation and request fulfillment logic.
     */
    @Transactional
    public NotificationResponseDto recordResponse(
        Long matchLogId,
        MatchOutcome responseOutcome,
        String recordedBy,
        String ipAddress
    ) {
        MatchLogEntity matchLog = matchLogRepository.findById(matchLogId)
            .orElseThrow(() -> new IllegalArgumentException("Match log not found: " + matchLogId));

        if (matchLog.getOutcome() != MatchOutcome.NOTIFIED) {
            throw new IllegalStateException("Match log has already been resolved with outcome: " + matchLog.getOutcome());
        }

        matchLog.setOutcome(responseOutcome);
        matchLog.setRespondedAt(Instant.now());
        matchLog = matchLogRepository.save(matchLog);

        DonorProfileEntity donor = matchLog.getDonor();
        BloodRequestEntity request = matchLog.getRequest();

        switch (responseOutcome) {
            case ACCEPTED -> {
                donor.setTotalAccepts(donor.getTotalAccepts() + 1);
                donor.setLastDonationDate(LocalDate.now());
                donorProfileRepository.save(donor);

                request.setUnitsFulfilled(request.getUnitsFulfilled() + 1);
                if (request.getUnitsFulfilled() >= request.getUnitsRequested()) {
                    request.setStatus(RequestStatus.FULFILLED);
                }
                bloodRequestRepository.save(request);
            }
            case DECLINED -> {
                donor.setTotalDeclines(donor.getTotalDeclines() + 1);
                donorProfileRepository.save(donor);
            }
            case EXPIRED, CANCELLED -> {
                donor.setTotalNoResponses(donor.getTotalNoResponses() + 1);
                donorProfileRepository.save(donor);
            }
            default -> {}
        }

        auditService.log(recordedBy, "SYSTEM", "DONOR_RESPONSE_RECORDED", "MATCH_LOG",
            matchLog.getId().toString(), "Recorded outcome: " + responseOutcome, ipAddress);

        return new NotificationResponseDto(
            matchLog.getId(),
            request.getId(),
            request.getPatientReference(),
            request.getBloodGroup(),
            request.getUrgency(),
            request.getZone(),
            request.getDeadline(),
            matchLog.getOutcome().name(),
            matchLog.getNotifiedAt()
        );
    }

    private BloodRequest toCoreRequest(BloodRequestEntity entity) {
        return new BloodRequest(
            entity.getId().toString(),
            BloodGroup.valueOf(entity.getBloodGroup()),
            entity.getUnitsRequested() - entity.getUnitsFulfilled(),
            Urgency.valueOf(entity.getUrgency().toUpperCase()),
            entity.getZone(),
            entity.getCreatedAt(),
            entity.getDeadline()
        );
    }

    private DonorSnapshot toDonorSnapshot(DonorProfileEntity entity) {
        Sex sex = "FEMALE".equalsIgnoreCase(entity.getSex()) ? Sex.FEMALE : Sex.MALE;
        BloodGroup bloodGroup = BloodGroup.valueOf(entity.getBloodGroup());
        Optional<CurrentLocation> location = (entity.getCurrentZone() != null && !entity.getCurrentZone().isBlank())
            ? Optional.of(new CurrentLocation(entity.getCurrentZone(), entity.getLocationConfirmedAt() != null ? entity.getLocationConfirmedAt() : Instant.now()))
            : Optional.empty();

        HistoryStats historyStats = new HistoryStats(
            entity.getTotalContacts(),
            entity.getTotalAccepts(),
            entity.getTotalDeclines(),
            entity.getTotalNoResponses(),
            null
        );

        return new DonorSnapshot(
            entity.getId().toString(),
            bloodGroup,
            sex,
            entity.getHomeZone(),
            entity.getLastDonationDate(),
            entity.isAvailable(),
            entity.isPaused(),
            location,
            historyStats
        );
    }

    private ZoneMap buildZoneMap() {
        Map<String, GeoCoordinate> centroids = new HashMap<>();
        List<BankEntity> banks = bankRepository.findAll();
        for (BankEntity b : banks) {
            centroids.putIfAbsent(b.getZone(), new GeoCoordinate(b.getLatitude(), b.getLongitude()));
        }
        // Fallback default coordinates for standard test zones
        centroids.putIfAbsent("Z1", new GeoCoordinate(28.6139, 77.2090));
        centroids.putIfAbsent("Z2", new GeoCoordinate(28.7041, 77.1025));
        centroids.putIfAbsent("Z3", new GeoCoordinate(28.5355, 77.3910));
        centroids.putIfAbsent("Z4", new GeoCoordinate(28.4595, 77.0266));
        centroids.putIfAbsent("Z5", new GeoCoordinate(28.5672, 77.3211));

        return new ZoneMap(centroids, 1.3);
    }

    private List<BankInventory> loadCoreBankInventories() {
        List<BankEntity> banks = bankRepository.findAll();
        List<BankInventoryEntity> allStock = bankInventoryRepository.findAll();

        Map<Long, Map<BloodGroup, Integer>> stockByBank = new HashMap<>();
        for (BankInventoryEntity inv : allStock) {
            stockByBank
                .computeIfAbsent(inv.getBank().getId(), k -> {
                    Map<BloodGroup, Integer> map = new EnumMap<>(BloodGroup.class);
                    for (BloodGroup bg : BloodGroup.values()) {
                        map.put(bg, 0);
                    }
                    return map;
                })
                .put(BloodGroup.valueOf(inv.getBloodGroup()), inv.getUnitsAvailable());
        }

        List<BankInventory> coreList = new ArrayList<>();
        for (BankEntity b : banks) {
            Map<BloodGroup, Integer> stock = stockByBank.computeIfAbsent(b.getId(), k -> {
                Map<BloodGroup, Integer> map = new EnumMap<>(BloodGroup.class);
                for (BloodGroup bg : BloodGroup.values()) {
                    map.put(bg, 0);
                }
                return map;
            });
            coreList.add(new BankInventory(
                b.getId().toString(),
                b.getName(),
                b.getZone(),
                stock,
                b.getContactPhone() != null ? b.getContactPhone() : "N/A"
            ));
        }

        return coreList;
    }

    private String serializeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
