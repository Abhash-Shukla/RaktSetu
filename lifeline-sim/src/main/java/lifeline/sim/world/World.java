package lifeline.sim.world;

import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.ZoneMap;
import lifeline.core.inventory.BankInventory;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.oracle.Oracle;
import lifeline.sim.oracle.SimulatedOracle;
import lifeline.sim.response.ResponseModel;

import java.time.Instant;
import java.util.*;

/**
 * Complete synthetic simulation world containing observable snapshots,
 * unobservable latent traits, bank inventories, and topology.
 */
public record World(
    ZoneMap zoneMap,
    Map<String, LatentDonor> latentDonors,
    List<BankInventory> bankInventories,
    SimulationParameters params,
    Instant createdAt
) {
    public World {
        Objects.requireNonNull(zoneMap, "zoneMap cannot be null");
        Objects.requireNonNull(latentDonors, "latentDonors cannot be null");
        Objects.requireNonNull(bankInventories, "bankInventories cannot be null");
        Objects.requireNonNull(params, "params cannot be null");
        Objects.requireNonNull(createdAt, "createdAt cannot be null");
        latentDonors = Collections.unmodifiableMap(new LinkedHashMap<>(latentDonors));
        bankInventories = Collections.unmodifiableList(List.copyOf(bankInventories));
    }

    /**
     * Extracts observable donor snapshots visible to the matching engine.
     * All latent traits remain hidden inside the simulator.
     */
    public List<DonorSnapshot> observableDonors() {
        return latentDonors.values().stream()
            .map(LatentDonor::snapshot)
            .toList();
    }

    /**
     * Alias for {@link #params()}.
     */
    public SimulationParameters parameters() {
        return params;
    }

    /**
     * Alias for {@link #createdAt()}.
     */
    public Instant referenceTime() {
        return createdAt;
    }

    /**
     * Creates an Oracle instance evaluating true acceptance probabilities
     * for evaluation baselines and regret analysis.
     */
    public Oracle createOracle(ResponseModel model) {
        return new SimulatedOracle(latentDonors, model);
    }
}
