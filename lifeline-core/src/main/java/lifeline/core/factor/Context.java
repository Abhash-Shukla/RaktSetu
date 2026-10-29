package lifeline.core.factor;

import lifeline.core.config.EngineConfig;
import lifeline.core.config.WeightProfile;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.PopulationFrequencyTable;
import lifeline.core.domain.ZoneMap;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable scoring context passed into {@link ScoreFactor#score(DonorSnapshot, BloodRequest, Context)}.
 *
 * @param zoneMap               topology map of zone centroids and road factor
 * @param freshnessWindow       maximum duration for current location to be fresh
 * @param referenceTime         evaluation timestamp (typically request creation time)
 * @param populationFrequencies ABO/Rh population frequency distribution
 * @param halfDistanceKm        travel tolerance distance where acceptance halves (P-18, default 10.0 km)
 * @param betaPriorAlpha        smoothing prior alpha for responsiveness (default 1.0)
 * @param betaPriorBeta         smoothing prior beta for responsiveness (default 2.0)
 * @param weightProfile         urgency-selected weights for this scoring run
 * @param activeFactors         set of active factor names for ablation (e.g., "PROXIMITY", "RARITY")
 */
public record Context(
    ZoneMap zoneMap,
    Duration freshnessWindow,
    Instant referenceTime,
    PopulationFrequencyTable populationFrequencies,
    double halfDistanceKm,
    double betaPriorAlpha,
    double betaPriorBeta,
    WeightProfile weightProfile,
    Set<String> activeFactors
) {
    public static final double DEFAULT_HALF_DISTANCE_KM = 10.0;
    public static final double DEFAULT_PRIOR_ALPHA = 1.0;
    public static final double DEFAULT_PRIOR_BETA = 2.0;

    public static final Set<String> DEFAULT_ACTIVE_FACTORS = Set.of(
        ProximityFactor.NAME,
        RarityFactor.NAME
    );

    public Context {
        Objects.requireNonNull(zoneMap, "zoneMap cannot be null");
        Objects.requireNonNull(freshnessWindow, "freshnessWindow cannot be null");
        Objects.requireNonNull(referenceTime, "referenceTime cannot be null");
        Objects.requireNonNull(populationFrequencies, "populationFrequencies cannot be null");
        Objects.requireNonNull(weightProfile, "weightProfile cannot be null");
        Objects.requireNonNull(activeFactors, "activeFactors cannot be null");
        if (halfDistanceKm <= 0.0) {
            throw new IllegalArgumentException("halfDistanceKm must be positive, got: " + halfDistanceKm);
        }
        activeFactors = Collections.unmodifiableSet(Set.copyOf(activeFactors));
    }

    /**
     * Checks if a factor is active in this context.
     */
    public boolean isFactorActive(String factorName) {
        return activeFactors.contains(factorName);
    }

    /**
     * Creates a context from an EngineConfig and ZoneMap for a specific blood request.
     */
    public static Context from(EngineConfig config, ZoneMap zoneMap, BloodRequest request) {
        WeightProfile weights = config.urgencyProfiles().get(request.urgency());
        Duration freshness = Duration.ofHours(config.geography().locationFreshnessHours());
        return new Context(
            zoneMap,
            freshness,
            request.createdAt(),
            PopulationFrequencyTable.defaultIndianPopulation(),
            DEFAULT_HALF_DISTANCE_KM,
            DEFAULT_PRIOR_ALPHA,
            DEFAULT_PRIOR_BETA,
            weights,
            DEFAULT_ACTIVE_FACTORS
        );
    }
}
