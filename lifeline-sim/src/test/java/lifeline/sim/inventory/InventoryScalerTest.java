package lifeline.sim.inventory;

import lifeline.core.domain.BloodGroup;
import lifeline.core.inventory.BankInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.InputStream;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryScalerTest {

    private Map<BloodGroup, Double> populationFrequencies;
    private List<String> zoneList;

    @BeforeEach
    void setUp() {
        populationFrequencies = loadPopulationFrequencies();
        zoneList = List.of("Z01", "Z02", "Z03", "Z04");
    }

    @Test
    @DisplayName("0% inventory level scales all blood groups to exactly zero units")
    void zeroInventoryProducesZeroUnits() {
        List<BankInventory> banks = InventoryScaler.scale(populationFrequencies, 0.0, zoneList);

        assertThat(banks).hasSize(4);
        for (BankInventory bank : banks) {
            for (BloodGroup bg : BloodGroup.values()) {
                assertThat(bank.unitsOf(bg))
                    .as("Stock of %s at bank %s must be 0 for 0%% inventory", bg, bank.bankId())
                    .isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("100% inventory level scales units to round(30.0 * P(g)) per zone")
    void fullInventoryScalesToExpectedDemand() {
        List<BankInventory> banks = InventoryScaler.scale(populationFrequencies, 1.0, zoneList);

        assertThat(banks).hasSize(4);
        for (BankInventory bank : banks) {
            int totalStock = 0;
            for (BloodGroup bg : BloodGroup.values()) {
                double freq = populationFrequencies.get(bg);
                int expectedUnits = (int) Math.round(30.0 * freq);
                assertThat(bank.unitsOf(bg)).isEqualTo(expectedUnits);
                totalStock += bank.unitsOf(bg);
            }
            // Sum of rounded 30.0 * P(g) for Indian population frequencies is 31
            assertThat(totalStock).isBetween(28, 32);
        }
    }

    @Test
    @DisplayName("25% and 50% inventory levels scale proportionally")
    void partialInventoryLevelsScaleProportionally() {
        List<BankInventory> banks25 = InventoryScaler.scale(populationFrequencies, 0.25, zoneList);
        List<BankInventory> banks50 = InventoryScaler.scale(populationFrequencies, 0.50, zoneList);

        for (int i = 0; i < zoneList.size(); i++) {
            BankInventory b25 = banks25.get(i);
            BankInventory b50 = banks50.get(i);

            int total25 = Arrays.stream(BloodGroup.values()).mapToInt(b25::unitsOf).sum();
            int total50 = Arrays.stream(BloodGroup.values()).mapToInt(b50::unitsOf).sum();

            assertThat(total25).isBetween(6, 9);   // ~7.5 units
            assertThat(total50).isBetween(14, 17); // ~15.0 units
            assertThat(total50).isGreaterThan(total25);
        }
    }

    @Test
    @DisplayName("Negative inventory level or empty zone list throws IllegalArgumentException")
    void invalidArgumentsThrowException() {
        assertThatThrownBy(() -> InventoryScaler.scale(populationFrequencies, -0.1, zoneList))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("inventoryLevel cannot be negative");

        assertThatThrownBy(() -> InventoryScaler.scale(populationFrequencies, 1.0, Collections.emptyList()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("zoneList cannot be empty");
    }

    @SuppressWarnings("unchecked")
    private static Map<BloodGroup, Double> loadPopulationFrequencies() {
        try (InputStream in = InventoryScalerTest.class.getResourceAsStream("/sim/population.yaml")) {
            Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
            Map<String, Object> root = yaml.load(in);
            Map<String, Object> freqs = (Map<String, Object>) root.get("frequencies");

            Map<BloodGroup, Double> map = new EnumMap<>(BloodGroup.class);
            for (Map.Entry<String, Object> entry : freqs.entrySet()) {
                map.put(BloodGroup.fromCode(entry.getKey()), ((Number) entry.getValue()).doubleValue());
            }
            return map;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

