package lifeline.core.factor;

import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.CurrentLocation;
import lifeline.core.domain.DonorSnapshot;

import java.time.Duration;

/**
 * Proximity factor evaluating road distance decay between donor and request.
 * <p>
 * Evaluates current location if confirmed within the configured freshness window;
 * otherwise evaluates the home zone and attaches the {@code STALE_LOCATION} reason code.
 */
public final class ProximityFactor implements ScoreFactor {

    public static final String NAME = "PROXIMITY";
    public static final String REASON_CURRENT = "PROXIMITY_CURRENT";
    public static final String REASON_HOME = "PROXIMITY_HOME";
    public static final String REASON_STALE_LOCATION = "STALE_LOCATION";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public FactorContribution score(DonorSnapshot d, BloodRequest r, Context ctx) {
        String zoneToUse;
        String reasonCode;

        if (d.currentLocation().isPresent()) {
            CurrentLocation loc = d.currentLocation().get();
            Duration age = Duration.between(loc.confirmedAt(), ctx.referenceTime());
            boolean isFresh = !age.isNegative() && age.compareTo(ctx.freshnessWindow()) <= 0;

            if (isFresh) {
                zoneToUse = loc.zone();
                reasonCode = REASON_CURRENT;
            } else {
                zoneToUse = d.homeZone();
                reasonCode = REASON_STALE_LOCATION;
            }
        } else {
            zoneToUse = d.homeZone();
            reasonCode = REASON_HOME;
        }

        double distanceKm = ctx.zoneMap().distanceKm(zoneToUse, r.zone());
        // Exponential half-distance decay: 2^(-d / d_half)
        double rawValue = Math.pow(2.0, -distanceKm / ctx.halfDistanceKm());
        double weight = ctx.weightProfile().proximity();

        return FactorContribution.of(NAME, rawValue, weight, reasonCode);
    }
}
