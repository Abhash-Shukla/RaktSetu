package lifeline.core.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class ZoneMapTest {

    @Test
    @DisplayName("Distance to identical zone is exactly 0.0")
    void sameZoneDistanceIsZero() {
        ZoneMap map = new ZoneMap(
            Map.of("Z1", new GeoCoordinate(19.0760, 72.8777)),
            1.3
        );

        assertThat(map.distanceKm("Z1", "Z1")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Haversine distance between two known coordinates scaled by road factor")
    void haversineScaledByRoadFactor() {
        // Mumbai (19.0760, 72.8777) to Pune (18.5204, 73.8567)
        // Great-circle distance is ~118.5 km. With road factor 1.3 -> ~154.0 km.
        ZoneMap map = new ZoneMap(
            Map.of(
                "MUMBAI", new GeoCoordinate(19.0760, 72.8777),
                "PUNE", new GeoCoordinate(18.5204, 73.8567)
            ),
            1.3
        );

        double dist = map.distanceKm("MUMBAI", "PUNE");
        assertThat(dist).isCloseTo(156.2, offset(0.5));

        // Symmetric
        assertThat(dist).isEqualTo(map.distanceKm("PUNE", "MUMBAI"));
    }

    @Test
    @DisplayName("Querying unknown zone throws IllegalArgumentException")
    void unknownZoneThrows() {
        ZoneMap map = new ZoneMap(
            Map.of("Z1", new GeoCoordinate(10.0, 10.0)),
            1.3
        );

        assertThatThrownBy(() -> map.distanceKm("Z1", "Z99"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown zone ID: Z99");
    }

    @Test
    @DisplayName("Invalid road factor below 1.0 throws IllegalArgumentException")
    void invalidRoadFactorThrows() {
        assertThatThrownBy(() -> new ZoneMap(Map.of(), 0.9))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("roadFactor must be at least 1.0");
    }
}
