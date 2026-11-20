package lifeline.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ParsedIntakeDto(
    @JsonProperty("bloodGroup") String bloodGroup,
    @JsonProperty("urgency") String urgency,
    @JsonProperty("zone/pincode") String zoneOrPincode,
    @JsonProperty("units") Integer units,
    @JsonProperty("contact") String contact,
    @JsonProperty("confidence") double confidence,
    @JsonProperty("unparsed_text") String unparsedText,
    @JsonProperty("fieldConfidences") Map<String, Double> fieldConfidences,
    @JsonProperty("flags") List<String> flags,
    @JsonProperty("isValid") boolean isValid,
    @JsonProperty("fallbackRecommended") boolean fallbackRecommended,
    @JsonProperty("fallbackReason") String fallbackReason
) {
    public ParsedIntakeDto {
        if (fieldConfidences == null) {
            fieldConfidences = Collections.emptyMap();
        }
        if (flags == null) {
            flags = Collections.emptyList();
        }
    }

    /**
     * Alias for zoneOrPincode for API convenience.
     */
    @JsonProperty("zone")
    public String zone() {
        return zoneOrPincode;
    }
}
