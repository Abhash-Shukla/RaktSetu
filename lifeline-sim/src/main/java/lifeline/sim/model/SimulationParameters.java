package lifeline.sim.model;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.Map;
import java.util.Objects;

/**
 * Typed simulation parameters loaded from simulation-parameters.yaml.
 * Anchored in docs/PARAMETERS.md.
 */
public record SimulationParameters(
    int donorPoolSize,
    int zoneCount,
    double densityLognormalSigma,
    double roadFactor,
    int horizonDays,
    double meanContactsPerDonor,
    int historyMonths,

    double maleShare,
    double femaleShare,
    double availabilityShare,
    double currentLocationShare,
    int freshnessWindowHours,
    int cooldownDaysMale,
    int cooldownDaysFemale,
    int maxDonationAgeDays,

    double meanWillingness,
    double heterogeneityKappa,
    double delayMedianMinutes,
    double delaySigma,
    double travelToleranceKm,

    double arrivalMeanPerZonePerDay,
    double urgencyShareCritical,
    double urgencyShareUrgent,
    double urgencyShareRoutine,
    int deadlineHoursCritical,
    int deadlineHoursUrgent,
    int deadlineHoursRoutine,
    int attemptBudget,
    int expiryMinutesCritical,
    int expiryMinutesUrgent,
    int expiryMinutesRoutine,

    double m1UrgencyCriticalBonus,
    double m2FatigueFactor,
    double m2TimeOfDayAmplitude,
    double m3DistanceCoefficient,
    double m3HiddenZoneAffinityLogit
) {
    private static final String DEFAULT_CONFIG = "/sim/simulation-parameters.yaml";

    public static SimulationParameters loadDefault() {
        try (InputStream in = SimulationParameters.class.getResourceAsStream(DEFAULT_CONFIG)) {
            if (in == null) {
                throw new IllegalStateException("Default simulation parameters not found at " + DEFAULT_CONFIG);
            }
            return load(in);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load simulation parameters", e);
        }
    }

    @SuppressWarnings("unchecked")
    public static SimulationParameters load(InputStream in) {
        Objects.requireNonNull(in, "in cannot be null");
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        Map<String, Object> root = yaml.load(in);

        Map<String, Object> world = (Map<String, Object>) root.get("world");
        Map<String, Object> demo = (Map<String, Object>) root.get("demographics");
        Map<String, Object> latent = (Map<String, Object>) root.get("latentDonor");
        Map<String, Object> req = (Map<String, Object>) root.get("requests");
        Map<String, Object> models = (Map<String, Object>) root.get("responseModels");
        Map<String, Object> m1 = (Map<String, Object>) models.get("m1");
        Map<String, Object> m2 = (Map<String, Object>) models.get("m2");
        Map<String, Object> m3 = (Map<String, Object>) models.get("m3");

        return new SimulationParameters(
            ((Number) world.get("donorPoolSize")).intValue(),
            ((Number) world.get("zoneCount")).intValue(),
            ((Number) world.get("densityLognormalSigma")).doubleValue(),
            ((Number) world.get("roadFactor")).doubleValue(),
            ((Number) world.get("horizonDays")).intValue(),
            ((Number) world.get("meanContactsPerDonor")).doubleValue(),
            ((Number) world.get("historyMonths")).intValue(),

            ((Number) demo.get("maleShare")).doubleValue(),
            ((Number) demo.get("femaleShare")).doubleValue(),
            ((Number) demo.get("availabilityShare")).doubleValue(),
            ((Number) demo.get("currentLocationShare")).doubleValue(),
            ((Number) demo.get("freshnessWindowHours")).intValue(),
            ((Number) demo.get("cooldownDaysMale")).intValue(),
            ((Number) demo.get("cooldownDaysFemale")).intValue(),
            ((Number) demo.get("maxDonationAgeDays")).intValue(),

            ((Number) latent.get("meanWillingness")).doubleValue(),
            ((Number) latent.get("heterogeneityKappa")).doubleValue(),
            ((Number) latent.get("delayMedianMinutes")).doubleValue(),
            ((Number) latent.get("delaySigma")).doubleValue(),
            ((Number) latent.get("travelToleranceKm")).doubleValue(),

            ((Number) req.get("arrivalMeanPerZonePerDay")).doubleValue(),
            ((Number) req.get("urgencyShareCritical")).doubleValue(),
            ((Number) req.get("urgencyShareUrgent")).doubleValue(),
            ((Number) req.get("urgencyShareRoutine")).doubleValue(),
            ((Number) req.get("deadlineHoursCritical")).intValue(),
            ((Number) req.get("deadlineHoursUrgent")).intValue(),
            ((Number) req.get("deadlineHoursRoutine")).intValue(),
            ((Number) req.get("attemptBudget")).intValue(),
            ((Number) req.get("expiryMinutesCritical")).intValue(),
            ((Number) req.get("expiryMinutesUrgent")).intValue(),
            ((Number) req.get("expiryMinutesRoutine")).intValue(),

            ((Number) m1.get("urgencyCriticalBonus")).doubleValue(),
            ((Number) m2.get("fatigueFactor")).doubleValue(),
            ((Number) m2.get("timeOfDayAmplitude")).doubleValue(),
            ((Number) m3.get("distanceCoefficient")).doubleValue(),
            ((Number) m3.get("hiddenZoneAffinityLogit")).doubleValue()
        );
    }

    public SimulationParameters withAttemptBudget(int newAttemptBudget) {
        return new SimulationParameters(
            donorPoolSize, zoneCount, densityLognormalSigma, roadFactor, horizonDays, meanContactsPerDonor, historyMonths,
            maleShare, femaleShare, availabilityShare, currentLocationShare, freshnessWindowHours, cooldownDaysMale, cooldownDaysFemale, maxDonationAgeDays,
            meanWillingness, heterogeneityKappa, delayMedianMinutes, delaySigma, travelToleranceKm,
            arrivalMeanPerZonePerDay, urgencyShareCritical, urgencyShareUrgent, urgencyShareRoutine,
            deadlineHoursCritical, deadlineHoursUrgent, deadlineHoursRoutine, newAttemptBudget,
            expiryMinutesCritical, expiryMinutesUrgent, expiryMinutesRoutine,
            m1UrgencyCriticalBonus, m2FatigueFactor, m2TimeOfDayAmplitude, m3DistanceCoefficient, m3HiddenZoneAffinityLogit
        );
    }
}
