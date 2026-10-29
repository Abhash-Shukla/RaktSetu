package lifeline.core.config;

/**
 * Top-N candidate selection configuration.
 *
 * @param defaultTopN number of top candidates to present to staff (default 5)
 */
public record SelectionConfig(
    int defaultTopN
) {
    public static final int DEFAULT_TOP_N = 5;

    public SelectionConfig {
        if (defaultTopN <= 0) {
            throw new IllegalArgumentException("defaultTopN must be positive, got: " + defaultTopN);
        }
    }

    public static SelectionConfig defaults() {
        return new SelectionConfig(DEFAULT_TOP_N);
    }
}
