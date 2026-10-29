package lifeline.core.inventory;

import java.util.Objects;

/**
 * Result of executing a Tier-0 simulated bank inventory check.
 */
public record Tier0Result(
    Tier0Status status,
    String bankId,
    String bankName,
    String bankZone,
    double distanceKm,
    int compatibleUnitsFound,
    String message
) {
    public enum Tier0Status {
        ROUTE_TO_BANK,
        FALL_THROUGH
    }

    public Tier0Result {
        Objects.requireNonNull(status, "status cannot be null");
        Objects.requireNonNull(message, "message cannot be null");
    }

    public static Tier0Result routeToBank(
        BankInventory bank,
        double distanceKm,
        int unitsFound
    ) {
        return new Tier0Result(
            Tier0Status.ROUTE_TO_BANK,
            bank.bankId(),
            bank.bankName(),
            bank.zone(),
            distanceKm,
            unitsFound,
            String.format("Tier 0 resolved: %d compatible units found at %s (zone %s, %.1f km away)",
                unitsFound, bank.bankName(), bank.zone(), distanceKm)
        );
    }

    public static Tier0Result fallThrough(String reason) {
        return new Tier0Result(
            Tier0Status.FALL_THROUGH,
            null,
            null,
            null,
            -1.0,
            0,
            reason
        );
    }
}
