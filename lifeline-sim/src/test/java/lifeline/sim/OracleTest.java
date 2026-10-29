package lifeline.sim;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.BloodRequest;
import lifeline.core.domain.Urgency;
import lifeline.sim.model.SimulationParameters;
import lifeline.sim.oracle.Oracle;
import lifeline.sim.oracle.SimulatedOracle;
import lifeline.sim.oracle.TrueProbability;
import lifeline.sim.response.M1BaselineLogisticModel;
import lifeline.sim.world.World;
import lifeline.sim.world.WorldGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OracleTest {

    @Test
    @DisplayName("Oracle interface returns TrueProbability strictly in [0.0, 1.0]")
    void oracleProvidesTrueProbability() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(42L);

        Oracle oracle = new SimulatedOracle(world.latentDonors(), new M1BaselineLogisticModel(params));

        Instant now = world.referenceTime();
        BloodRequest req = new BloodRequest(
            "REQ-ORACLE",
            BloodGroup.O_POS,
            1,
            Urgency.CRITICAL,
            "Z01",
            now,
            now.plus(Duration.ofHours(2))
        );

        String sampleDonorId = world.latentDonors().keySet().iterator().next();

        TrueProbability tp = oracle.queryProbability(sampleDonorId, req, 5.0, 0, 14.0);
        assertThat(tp.value()).isBetween(0.0, 1.0);

        double rawP = oracle.trueAcceptanceProbability(sampleDonorId, req, 5.0, 0, 14.0);
        assertThat(rawP).isEqualTo(tp.value());
    }

    @Test
    @DisplayName("Oracle returns 0.0 for unknown donor ID")
    void oracleReturnsZeroForUnknownDonor() {
        SimulationParameters params = SimulationParameters.loadDefault();
        WorldGenerator gen = new WorldGenerator(params);
        World world = gen.generate(42L);

        Oracle oracle = new SimulatedOracle(world.latentDonors(), new M1BaselineLogisticModel(params));
        Instant now = world.referenceTime();
        BloodRequest req = new BloodRequest(
            "REQ-ORACLE",
            BloodGroup.O_POS,
            1,
            Urgency.CRITICAL,
            "Z01",
            now,
            now.plus(Duration.ofHours(2))
        );

        assertThat(oracle.trueAcceptanceProbability("UNKNOWN-DONOR", req, 5.0, 0, 14.0))
            .isEqualTo(0.0);
    }
}
