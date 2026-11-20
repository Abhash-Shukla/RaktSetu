package lifeline.api.exception;

import java.util.Collections;
import java.util.List;

public class InvalidIntakeException extends RuntimeException {

    private final List<String> flags;
    private final String fallbackUrl;

    public InvalidIntakeException(String message) {
        this(message, Collections.emptyList(), "/api/v1/public/requests");
    }

    public InvalidIntakeException(String message, List<String> flags, String fallbackUrl) {
        super(message);
        this.flags = flags != null ? flags : Collections.emptyList();
        this.fallbackUrl = fallbackUrl;
    }

    public List<String> getFlags() {
        return flags;
    }

    public String getFallbackUrl() {
        return fallbackUrl;
    }
}
