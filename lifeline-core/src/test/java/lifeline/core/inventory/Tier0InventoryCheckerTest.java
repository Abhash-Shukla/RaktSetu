package lifeline.core.inventory;

import lifeline.core.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Tier0InventoryCheckerTest {

    private ZoneMap zoneMap;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-10-15T10:00:00Z");
        // Z1 at (0, 0), Z2 at ~10 km away, Z3 at ~50 km away
        zoneMap = new ZoneMap(
            Map.of(
                "Z1", new GeoCoordinate(19.0000, 72.8000),
                "Z2", new GeoCoordinate(19.0600, 72.8500),
                "Z3", new GeoCoordinate(19.4000, 72.8500)
            ),
            1.3
        );
    }

    private BloodRequest createRequest(BloodGroup group, int units, String zone) {
        return new BloodRequest("REQ-T0", group, units, Urgency.URGENT, zone, now, now.plusSeconds(3600));
    }

    @Test
    @DisplayName("Tier-0 boundary: exactly enough compatible units (2 requested, 2 available) within radius routes to bank")
    void exactlyEnoughStockRoutesToBank() {
        Tier0InventoryChecker checker = new Tier0InventoryChecker(zoneMap, 25.0);
        BloodRequest req = createRequest(BloodGroup.B_POS, 2, "Z1");

        // Bank in Z2 (~10km away) has exactly 2 units of B+
        BankInventory bank = new BankInventory(
            "BANK-1",
            "City Central Blood Bank",
            "Z2",
            Map.of(BloodGroup.B_POS, 2),
            "+91-9876543210"
        );

        Tier0Result result = checker.check(req, List.of(bank));

        assertThat(result.status()).isEqualTo(Tier0Result.Tier0Status.ROUTE_TO_BANK);
        assertThat(result.bankId()).isEqualTo("BANK-1");
        assertThat(result.compatibleUnitsFound()).isEqualTo(2);
        assertThat(result.distanceKm()).isLessThanOrEqualTo(25.0);
    }

    @Test
    @DisplayName("Tier-0 boundary: one short (2 requested, 1 available) within radius falls through")
    void oneShortFallsThrough() {
        Tier0InventoryChecker checker = new Tier0InventoryChecker(zoneMap, 25.0);
        BloodRequest req = createRequest(BloodGroup.B_POS, 2, "Z1");

        // Bank in Z2 has only 1 compatible unit
        BankInventory bank = new BankInventory(
            "BANK-1",
            "City Central Blood Bank",
            "Z2",
            Map.of(BloodGroup.B_POS, 1),
            "+91-9876543210"
        );

        Tier0Result result = checker.check(req, List.of(bank));

        assertThat(result.status()).isEqualTo(Tier0Result.Tier0Status.FALL_THROUGH);
        assertThat(result.message()).contains("Insufficient compatible stock");
    }

    @Test
    @DisplayName("Tier-0 boundary: abundant stock but outside configured radius falls through")
    void abundantStockOutOfRadiusFallsThrough() {
        Tier0InventoryChecker checker = new Tier0InventoryChecker(zoneMap, 20.0);
        BloodRequest req = createRequest(BloodGroup.B_POS, 2, "Z1");

        // Bank in Z3 is ~50km away (well outside 20km radius), with 10 units
        BankInventory bankInZ3 = new BankInventory(
            "BANK-DISTANT",
            "Distant District Hospital",
            "Z3",
            Map.of(BloodGroup.B_POS, 10),
            "+91-9876543210"
        );

        Tier0Result result = checker.check(req, List.of(bankInZ3));

        assertThat(result.status()).isEqualTo(Tier0Result.Tier0Status.FALL_THROUGH);
    }

    @Test
    @DisplayName("Stock compatibility rule: compatible subtypes (e.g. O+ or O- for B+ recipient) count towards available stock")
    void compatibleSubtypesCountTowardsStock() {
        Tier0InventoryChecker checker = new Tier0InventoryChecker(zoneMap, 25.0);
        BloodRequest req = createRequest(BloodGroup.B_POS, 3, "Z1");

        // Bank in Z2 has 1 unit B+, 1 unit O+, 1 unit O- (all 3 compatible with B+ recipient)
        BankInventory bank = new BankInventory(
            "BANK-COMPAT",
            "Metro Blood Bank",
            "Z2",
            Map.of(
                BloodGroup.B_POS, 1,
                BloodGroup.O_POS, 1,
                BloodGroup.O_NEG, 1,
                BloodGroup.A_POS, 5 // Incompatible with B+, must not count
            ),
            "+91-9876543210"
        );

        Tier0Result result = checker.check(req, List.of(bank));

        assertThat(result.status()).isEqualTo(Tier0Result.Tier0Status.ROUTE_TO_BANK);
        assertThat(result.compatibleUnitsFound()).isEqualTo(3);
    }

    @Test
    @DisplayName("Check all eligible banks: an insufficient nearest bank does not hide sufficient stock at another eligible bank within radius")
    void insufficientNearestBankDoesNotHideSufficientFartherBank() {
        Tier0InventoryChecker checker = new Tier0InventoryChecker(zoneMap, 25.0);
        BloodRequest req = createRequest(BloodGroup.B_POS, 2, "Z1");

        // Nearest bank is in Z1 (distance = 0 km), but has only 1 unit (insufficient for 2 units requested)
        BankInventory nearestBankInsufficient = new BankInventory(
            "BANK-NEAR",
            "Immediate Clinic",
            "Z1",
            Map.of(BloodGroup.B_POS, 1),
            "+91-1111"
        );

        // Farther bank is in Z2 (~10 km away, well within 25 km radius), and has 3 units (sufficient)
        BankInventory fartherBankSufficient = new BankInventory(
            "BANK-FARTHER",
            "Regional Blood Center",
            "Z2",
            Map.of(BloodGroup.B_POS, 3),
            "+91-2222"
        );

        Tier0Result result = checker.check(req, List.of(nearestBankInsufficient, fartherBankSufficient));

        // Must successfully route to the farther bank with sufficient stock, NOT fall through
        assertThat(result.status()).isEqualTo(Tier0Result.Tier0Status.ROUTE_TO_BANK);
        assertThat(result.bankId()).isEqualTo("BANK-FARTHER");
        assertThat(result.compatibleUnitsFound()).isEqualTo(3);
        assertThat(result.distanceKm()).isCloseTo(zoneMap.distanceKm("Z2", "Z1"), org.assertj.core.data.Offset.offset(0.01));
    }
}
