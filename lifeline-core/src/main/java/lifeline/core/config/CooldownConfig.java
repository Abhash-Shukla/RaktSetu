package lifeline.core.config;

import lifeline.core.domain.Sex;

import java.util.Objects;

/**
 * Statutory blood donation cooldown configuration per NBTC guidance.
 *
 * @param cooldownDaysMale   minimum donation interval for male donors (default 90 days)
 * @param cooldownDaysFemale minimum donation interval for female donors (default 120 days)
 */
public record CooldownConfig(
    int cooldownDaysMale,
    int cooldownDaysFemale
) {
    public static final int DEFAULT_MALE_DAYS = 90;
    public static final int DEFAULT_FEMALE_DAYS = 120;

    public CooldownConfig {
        if (cooldownDaysMale < 0 || cooldownDaysFemale < 0) {
            throw new IllegalArgumentException("Cooldown days cannot be negative");
        }
    }

    public static CooldownConfig defaults() {
        return new CooldownConfig(DEFAULT_MALE_DAYS, DEFAULT_FEMALE_DAYS);
    }

    /**
     * Resolves the required cooldown interval in days based strictly on biological sex.
     */
    public int cooldownDaysFor(Sex sex) {
        Objects.requireNonNull(sex, "sex cannot be null");
        return switch (sex) {
            case MALE -> cooldownDaysMale;
            case FEMALE -> cooldownDaysFemale;
        };
    }
}
