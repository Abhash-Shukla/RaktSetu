package lifeline.eval.demo;

import lifeline.core.domain.CurrentLocation;
import lifeline.core.domain.DonorSnapshot;
import lifeline.core.domain.GeoCoordinate;
import lifeline.core.inventory.BankInventory;
import lifeline.core.state.DonorState;
import lifeline.sim.world.World;
import lifeline.sim.world.WorldGenerator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Exports synthetic world entities (observable donors and blood banks) into a structured
 * seed file (JSON) for loading by the API under the demo profile.
 * <p>
 * Evaluation integrity:
 * - Observable donor snapshots only; latent traits are never exported.
 * - Conforms to AGENTS.md Section 2: lifeline-api must never import lifeline-sim or lifeline-eval.
 */
public final class DemoDataExporter {

    private DemoDataExporter() {
    }

    /**
     * Generates a synthetic world with the given seed and exports observable entities to the specified output file.
     *
     * @param seed       random seed
     * @param donorLimit maximum number of donors to export (e.g. 200)
     * @param outputPath destination path for demo seed JSON
     * @throws IOException on I/O error
     */
    public static void exportDemoData(long seed, int donorLimit, Path outputPath) throws IOException {
        Objects.requireNonNull(outputPath, "outputPath cannot be null");
        WorldGenerator generator = new WorldGenerator();
        World world = generator.generate(seed);

        StringBuilder json = new StringBuilder(1024 * 1024);
        json.append("{\n");
        json.append("  \"exportedAt\": \"").append(Instant.now().toString()).append("\",\n");
        json.append("  \"seed\": ").append(seed).append(",\n");

        // Banks
        json.append("  \"banks\": [\n");
        List<BankInventory> banks = world.bankInventories();
        for (int i = 0; i < banks.size(); i++) {
            BankInventory b = banks.get(i);
            GeoCoordinate coord = world.zoneMap().centroids().get(b.zone());
            double lat = coord != null ? coord.latitude() : 28.6139;
            double lon = coord != null ? coord.longitude() : 77.2090;

            json.append("    {\n");
            json.append("      \"bankId\": \"").append(escapeJson(b.bankId())).append("\",\n");
            json.append("      \"name\": \"").append(escapeJson(b.bankName())).append("\",\n");
            json.append("      \"zone\": \"").append(escapeJson(b.zone())).append("\",\n");
            json.append("      \"contactPhone\": \"").append(escapeJson(b.contactPhone())).append("\",\n");
            json.append("      \"contactEmail\": \"info@").append(b.bankId().toLowerCase()).append(".lifeline.test\",\n");
            json.append("      \"latitude\": ").append(lat).append(",\n");
            json.append("      \"longitude\": ").append(lon).append(",\n");
            json.append("      \"stock\": {\n");

            int sIdx = 0;
            for (Map.Entry<lifeline.core.domain.BloodGroup, Integer> entry : b.stock().entrySet()) {
                json.append("        \"").append(entry.getKey().name()).append("\": ").append(entry.getValue());
                if (++sIdx < b.stock().size()) {
                    json.append(",");
                }
                json.append("\n");
            }
            json.append("      }\n");
            json.append("    }");
            if (i < banks.size() - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ],\n");

        // Donors
        json.append("  \"donors\": [\n");
        List<DonorSnapshot> allDonors = world.observableDonors();
        int count = Math.min(donorLimit > 0 ? donorLimit : allDonors.size(), allDonors.size());

        for (int i = 0; i < count; i++) {
            DonorSnapshot d = allDonors.get(i);
            String username = "donor_" + d.id().toLowerCase().replace("-", "_");
            String email = username + "@lifeline.test";

            json.append("    {\n");
            json.append("      \"donorId\": \"").append(escapeJson(d.id())).append("\",\n");
            json.append("      \"username\": \"").append(escapeJson(username)).append("\",\n");
            json.append("      \"email\": \"").append(escapeJson(email)).append("\",\n");
            json.append("      \"bloodGroup\": \"").append(d.bloodGroup().name()).append("\",\n");
            json.append("      \"sex\": \"").append(d.sex().name()).append("\",\n");
            json.append("      \"homeZone\": \"").append(escapeJson(d.homeZone())).append("\",\n");
            json.append("      \"lastDonationDate\": ").append(d.lastDonationDate() != null ? "\"" + d.lastDonationDate() + "\"" : "null").append(",\n");
            json.append("      \"available\": ").append(d.state() == DonorState.AVAILABLE).append(",\n");
            json.append("      \"paused\": ").append(d.state() == DonorState.PAUSED).append(",\n");

            String curZone = d.currentLocation().map(CurrentLocation::zone).orElse(null);
            Instant locTime = d.currentLocation().map(CurrentLocation::confirmedAt).orElse(null);
            json.append("      \"currentZone\": ").append(curZone != null ? "\"" + escapeJson(curZone) + "\"" : "null").append(",\n");
            json.append("      \"locationConfirmedAt\": ").append(locTime != null ? "\"" + locTime + "\"" : "null").append(",\n");
            json.append("      \"totalContacts\": ").append(d.historyStats().contacts()).append(",\n");
            json.append("      \"totalAccepts\": ").append(d.historyStats().accepts()).append(",\n");
            json.append("      \"totalDeclines\": ").append(d.historyStats().declines()).append(",\n");
            json.append("      \"totalNoResponses\": ").append(d.historyStats().noResponses()).append("\n");
            json.append("    }");
            if (i < count - 1) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");

        if (outputPath.getParent() != null) {
            Files.createDirectories(outputPath.getParent());
        }
        Files.writeString(outputPath, json.toString(), StandardCharsets.UTF_8);
    }

    private static String escapeJson(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
