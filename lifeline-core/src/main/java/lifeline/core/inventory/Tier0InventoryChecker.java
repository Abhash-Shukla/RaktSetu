package lifeline.core.inventory;

import lifeline.core.compatibility.CompatibilityTable;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.ZoneMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;

/**
 * Evaluates Tier-0 blood bank inventory before any donor mobilisation.
 * <p>
 * Under SRS Section 7.5 (T0-1, T0-2, T0-3):
 * <ul>
 *   <li>Checks compatible stock within a configurable radius of the request zone.</li>
 *   <li>Stock Compatibility Rule: A stock unit is compatible if and only if
 *       {@link CompatibilityTable#isCompatible(lifeline.core.domain.BloodGroup, lifeline.core.domain.BloodGroup)} is true.</li>
 *   <li>If sufficient compatible units (>= requested units) exist at a nearby bank, returns ROUTE_TO_BANK.</li>
 *   <li>Otherwise, falls through to Tier-1 donor matching without contacting donors.</li>
 * </ul>
 */
public final class Tier0InventoryChecker {

    private static final Logger log = LoggerFactory.getLogger(Tier0InventoryChecker.class);

    public static final double DEFAULT_RADIUS_KM = 25.0;

    private final ZoneMap zoneMap;
    private final double maxRadiusKm;

    public Tier0InventoryChecker(ZoneMap zoneMap, double maxRadiusKm) {
        this.zoneMap = Objects.requireNonNull(zoneMap, "zoneMap cannot be null");
        if (maxRadiusKm <= 0.0) {
            throw new IllegalArgumentException("maxRadiusKm must be positive, got: " + maxRadiusKm);
        }
        this.maxRadiusKm = maxRadiusKm;
    }

    public Tier0InventoryChecker(ZoneMap zoneMap) {
        this(zoneMap, DEFAULT_RADIUS_KM);
    }

    /**
     * Checks simulated bank inventories against an active blood request using the instance radius.
     *
     * @param request target blood request
     * @param banks   available blood banks with current stock
     * @return ROUTE_TO_BANK if sufficient compatible units are nearby, or FALL_THROUGH
     */
    public Tier0Result check(BloodRequest request, Collection<BankInventory> banks) {
        return check(request, banks, this.maxRadiusKm);
    }

    /**
     * Checks simulated bank inventories against an active blood request with an explicit search radius.
     *
     * @param request     target blood request
     * @param banks       available blood banks with current stock
     * @param searchRadius search radius in kilometers
     * @return ROUTE_TO_BANK if sufficient compatible units are nearby, or FALL_THROUGH
     */
    public Tier0Result check(BloodRequest request, Collection<BankInventory> banks, double searchRadius) {
        Objects.requireNonNull(request, "request cannot be null");
        Objects.requireNonNull(banks, "banks cannot be null");

        log.debug("[request={}] Checking Tier-0 inventory across {} banks within radius {} km",
            request.id(), banks.size(), searchRadius);

        record CandidateBank(BankInventory bank, double distanceKm, int compatibleUnits) {}

        CandidateBank bestBank = banks.stream()
            .filter(b -> zoneMap.containsZone(b.zone()))
            .map(b -> {
                double dist = zoneMap.distanceKm(b.zone(), request.zone());
                int compUnits = b.compatibleUnitsFor(request.bloodGroup());
                return new CandidateBank(b, dist, compUnits);
            })
            .filter(cb -> cb.distanceKm <= searchRadius)
            .filter(cb -> cb.compatibleUnits >= request.units())
            .min(Comparator.comparingDouble(CandidateBank::distanceKm))
            .orElse(null);

        if (bestBank != null) {
            log.debug("[request={}] Tier-0 resolved at bank {} ({} units compatible, distance: {} km)",
                request.id(), bestBank.bank().bankName(), bestBank.compatibleUnits(), bestBank.distanceKm());
            return Tier0Result.routeToBank(bestBank.bank(), bestBank.distanceKm(), bestBank.compatibleUnits());
        }

        log.debug("[request={}] Tier-0 insufficient stock nearby; falling through to donor matching", request.id());
        return Tier0Result.fallThrough("Insufficient compatible stock within " + searchRadius + " km");
    }
}
