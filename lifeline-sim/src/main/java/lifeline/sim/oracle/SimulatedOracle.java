package lifeline.sim.oracle;

import lifeline.core.domain.BloodRequest;
import lifeline.sim.model.LatentDonor;
import lifeline.sim.response.ResponseModel;

import java.util.Map;
import java.util.Objects;

/**
 * Concrete implementation of {@link Oracle} wrapping latent donors and a response model.
 */
public final class SimulatedOracle implements Oracle {

    private final Map<String, LatentDonor> latentDonors;
    private final ResponseModel responseModel;

    public SimulatedOracle(Map<String, LatentDonor> latentDonors, ResponseModel responseModel) {
        this.latentDonors = Objects.requireNonNull(latentDonors, "latentDonors cannot be null");
        this.responseModel = Objects.requireNonNull(responseModel, "responseModel cannot be null");
    }

    @Override
    public double trueAcceptanceProbability(
        String donorId,
        BloodRequest request,
        double distanceKm,
        int contactsLast30Days,
        double timeOfDayHour
    ) {
        LatentDonor donor = latentDonors.get(donorId);
        if (donor == null) {
            return 0.0;
        }
        return responseModel.trueAcceptanceProbability(donor, request, distanceKm, contactsLast30Days, timeOfDayHour);
    }
}
