package lifeline.eval.stats;

import lifeline.eval.metric.TidyMetricRow;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Executes evaluation statistical analysis strictly per docs/EVALUATION_PROTOCOL.md Section 6.
 * Performs paired comparisons across seeds, Holm correction, effect sizes, bootstrap CIs,
 * and reports wins, losses, and ties. Emits paper-ready CSV and Markdown/LaTeX reports.
 * <p>
 * Hypothesis Family Partitioning Interpretation:
 * The protocol states: "Primary comparison: S6 (and S7) vs each of S0, S2, S3, S4, S5 — paired Wilcoxon
 * signed-rank across seeds, Holm correction, effect size". Because the protocol does not explicitly define
 * the exact family partitioning for family-wise error rate (FWER) control, this implementation's
 * interpretation partitions hypothesis families by the tuple {@code (candidateSystem, responseModel, metric)}.
 * Under this interpretation:
 * 1. S6 (locked spec engine) and S7 (responsiveness extension) are treated as separate candidate systems,
 *    each evaluated against its reference baseline family {S0, S2, S3, S4, S5} (m = 5 comparisons each).
 * 2. Grouping is strictly partitioned by response model and metric without cross-metric or cross-model pooling.
 */
public final class StatisticalAnalyzer {

    public static final double DEFAULT_ALPHA = 0.05;
    public static final long DEFAULT_BOOTSTRAP_SEED = 42L;

    /**
     * Minimum number of genuinely paired observations required to attempt inferential hypothesis testing.
     * <p>
     * Mathematical Foundation:
     * For a two-sided exact Wilcoxon signed-rank test without ties, the minimum possible unadjusted p-value
     * for sample size N is 2 / 2^N.
     * - At N = 5: min p = 2 / 32 = 0.0625 > 0.05. It is mathematically impossible to reject H0 at alpha = 0.05.
     * - At N = 6: min p = 2 / 64 = 0.03125 <= 0.05. N = 6 is the absolute mathematical lower bound for a raw p < 0.05.
     * <p>
     * Holm Step-Down Multiple Comparison Effect:
     * While N = 6 permits a raw p-value below 0.05 in the ideal case, it does NOT permit Holm-adjusted significance
     * when evaluating across a hypothesis family of m baselines. For example, testing candidate S6 against the m = 5
     * protocol baselines (S0, S2, S3, S4, S5) inflates the smallest p-value by m = 5, yielding a minimum attainable
     * Holm-adjusted p-value of 5 * 0.03125 = 0.15625 > 0.05. Achieving Holm-adjusted significance at alpha = 0.05
     * across m = 5 comparisons requires at least N = 8 (where min p_adj = 5 * (2 / 256) = 0.03906 <= 0.05).
     * <p>
     * Protocol Requirement Distinction:
     * While N >= 6 allows exploratory hypothesis testing on tuning seeds (1000+), pre-registered
     * research conclusions for E1 per docs/EVALUATION_PROTOCOL.md Section 6 strictly require all
     * {@value #REQUIRED_REPORTING_SEEDS} reporting seeds (seeds 1-30), where min p ~ 1.86e-9.
     */
    public static final int MIN_PAIRED_SEEDS_FOR_INFERENCE = 6;
    public static final int REQUIRED_REPORTING_SEEDS = 30;

    public static final List<String> DEFAULT_CANDIDATES = List.of("S6", "S7");
    public static final List<String> DEFAULT_BASELINES = List.of("S0", "S2", "S3", "S4", "S5");

    public static final Map<String, List<String>> E2_MAPPINGS = Map.of(
        "S6", List.of("S6_NO_PROXIMITY", "S6_NO_RARITY", "S6_NO_FRESHNESS", "S6_SINGLE_URGENCY"),
        "S7", List.of("S7_NO_RESPONSIVENESS")
    );

    public static final Map<String, List<String>> E3_MAPPINGS = Map.of(
        "S6", List.of("S6_NO_RARITY")
    );

    public static final Map<String, List<String>> E4_MAPPINGS = Map.of(
        "S6_TIER0_ON", List.of("S6_TIER0_OFF")
    );

    public static final Map<String, List<String>> E5_MAPPINGS = Map.of(
        "S6_FALLBACK_ON", List.of("S6_FALLBACK_OFF")
    );

    public static final Map<String, List<String>> E6_MAPPINGS = Map.of(
        "S6_DEFAULT", List.of("S6_PERTURBED_BEST", "S6_PERTURBED_MIN", "S6_SINGLE_URGENCY")
    );

    private final double alpha;
    private final long bootstrapSeed;
    private final List<String> candidateSystems;
    private final List<String> baselineSystems;
    private final Map<String, List<String>> candidateToBaselines;

    public StatisticalAnalyzer(double alpha, long bootstrapSeed, Map<String, List<String>> candidateToBaselines) {
        this.alpha = alpha > 0.0 && alpha < 1.0 ? alpha : DEFAULT_ALPHA;
        this.bootstrapSeed = bootstrapSeed;
        Objects.requireNonNull(candidateToBaselines, "candidateToBaselines cannot be null");
        Map<String, List<String>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : candidateToBaselines.entrySet()) {
            copy.put(e.getKey(), List.copyOf(e.getValue()));
        }
        this.candidateToBaselines = Collections.unmodifiableMap(copy);
        this.candidateSystems = List.copyOf(copy.keySet());
        Set<String> allBases = new LinkedHashSet<>();
        copy.values().forEach(allBases::addAll);
        this.baselineSystems = List.copyOf(allBases);
    }

    public StatisticalAnalyzer(double alpha, long bootstrapSeed, List<String> candidates, List<String> baselines) {
        this.alpha = alpha > 0.0 && alpha < 1.0 ? alpha : DEFAULT_ALPHA;
        this.bootstrapSeed = bootstrapSeed;
        this.candidateSystems = candidates != null && !candidates.isEmpty() ? List.copyOf(candidates) : DEFAULT_CANDIDATES;
        this.baselineSystems = baselines != null && !baselines.isEmpty() ? List.copyOf(baselines) : DEFAULT_BASELINES;
        Map<String, List<String>> map = new LinkedHashMap<>();
        for (String c : this.candidateSystems) {
            map.put(c, this.baselineSystems);
        }
        this.candidateToBaselines = Collections.unmodifiableMap(map);
    }

    public StatisticalAnalyzer() {
        this(DEFAULT_ALPHA, DEFAULT_BOOTSTRAP_SEED, DEFAULT_CANDIDATES, DEFAULT_BASELINES);
    }

    public static StatisticalAnalyzer forExperiment(String experiment) {
        if ("E2".equalsIgnoreCase(experiment)) {
            return new StatisticalAnalyzer(DEFAULT_ALPHA, DEFAULT_BOOTSTRAP_SEED, E2_MAPPINGS);
        }
        if (experiment != null && (experiment.equalsIgnoreCase("E3") || experiment.toUpperCase().startsWith("E3_") || experiment.toUpperCase().startsWith("E3-"))) {
            return new StatisticalAnalyzer(DEFAULT_ALPHA, DEFAULT_BOOTSTRAP_SEED, E3_MAPPINGS);
        }
        if (experiment != null && (experiment.equalsIgnoreCase("E4") || experiment.toUpperCase().startsWith("E4_") || experiment.toUpperCase().startsWith("E4-"))) {
            return new StatisticalAnalyzer(DEFAULT_ALPHA, DEFAULT_BOOTSTRAP_SEED, E4_MAPPINGS);
        }
        if (experiment != null && (experiment.equalsIgnoreCase("E5") || experiment.toUpperCase().startsWith("E5_") || experiment.toUpperCase().startsWith("E5-"))) {
            return new StatisticalAnalyzer(DEFAULT_ALPHA, DEFAULT_BOOTSTRAP_SEED, E5_MAPPINGS);
        }
        if (experiment != null && (experiment.equalsIgnoreCase("E6") || experiment.toUpperCase().startsWith("E6_") || experiment.toUpperCase().startsWith("E6-"))) {
            return new StatisticalAnalyzer(DEFAULT_ALPHA, DEFAULT_BOOTSTRAP_SEED, E6_MAPPINGS);
        }
        return new StatisticalAnalyzer();
    }

    public List<String> candidateSystems() {
        return candidateSystems;
    }

    public List<String> baselineSystems() {
        return baselineSystems;
    }

    public Map<String, List<String>> candidateToBaselines() {
        return candidateToBaselines;
    }

    /**
     * Validates and classifies evaluation seeds strictly per docs/EVALUATION_PROTOCOL.md Section 6.
     * Enforces that reporting seeds (1-30) and tuning seeds (1000+) are never mixed.
     *
     * @param seeds set of evaluation seeds
     * @return classified dataset type
     * @throws IllegalArgumentException if mixed, empty, or invalid seeds are found
     */
    public static EvaluationDatasetType classifyAndValidateSeeds(Set<Long> seeds) {
        Objects.requireNonNull(seeds, "seeds cannot be null");
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("Seed set cannot be empty");
        }

        boolean hasReporting = false;
        boolean hasTuning = false;

        for (long s : seeds) {
            if (s >= 1 && s <= 30) {
                hasReporting = true;
            } else if (s >= 1000) {
                hasTuning = true;
            } else {
                throw new IllegalArgumentException(String.format(
                    "Invalid seed %d detected. Allowed seeds are reporting seeds (1-30) or tuning seeds (1000+) per docs/EVALUATION_PROTOCOL.md.",
                    s));
            }
        }

        if (hasReporting && hasTuning) {
            throw new IllegalArgumentException(
                "Mixed seed types detected: reporting seeds (1-30) and tuning seeds (1000+) must never be mixed per docs/EVALUATION_PROTOCOL.md Section 6.");
        }

        if (hasReporting) {
            if (seeds.size() == REQUIRED_REPORTING_SEEDS) {
                boolean allPresent = true;
                for (long s = 1; s <= 30; s++) {
                    if (!seeds.contains(s)) {
                        allPresent = false;
                        break;
                    }
                }
                if (allPresent) {
                    return EvaluationDatasetType.FINAL_REPORTING;
                }
            }
            return EvaluationDatasetType.INCOMPLETE_REPORTING;
        }

        return EvaluationDatasetType.EXPLORATORY_TUNING;
    }

    /**
     * Runs statistical analysis across all observations.
     *
     * @param rows tidy evaluation rows
     * @return list of evaluated statistical comparisons
     */
    public List<StatisticalComparison> analyze(List<TidyMetricRow> rows) {
        Objects.requireNonNull(rows, "rows cannot be null");
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<Long> allSeeds = new HashSet<>();
        for (TidyMetricRow row : rows) {
            allSeeds.add(row.seed());
        }
        EvaluationDatasetType datasetType = classifyAndValidateSeeds(allSeeds);

        String experiment = rows.get(0).experiment();

        // Index data: Model -> Metric -> System -> (Seed -> Value)
        Map<String, Map<String, Map<String, Map<Long, Double>>>> data = new TreeMap<>();
        for (TidyMetricRow row : rows) {
            Map<Long, Double> seedMap = data.computeIfAbsent(row.responseModel(), k -> new TreeMap<>())
                .computeIfAbsent(row.metric(), k -> new TreeMap<>())
                .computeIfAbsent(row.system(), k -> new TreeMap<>());
            if (seedMap.containsKey(row.seed())) {
                throw new IllegalArgumentException(String.format(
                    "Duplicate metric record detected for experiment=%s, model=%s, metric=%s, system=%s, seed=%d",
                    row.experiment(), row.responseModel(), row.metric(), row.system(), row.seed()));
            }
            seedMap.put(row.seed(), row.value());
        }

        List<StatisticalComparison> allComparisons = new ArrayList<>();

        for (Map.Entry<String, Map<String, Map<String, Map<Long, Double>>>> modelEntry : data.entrySet()) {
            String model = modelEntry.getKey();
            Map<String, Map<String, Map<Long, Double>>> metricsMap = modelEntry.getValue();

            for (Map.Entry<String, Map<String, Map<Long, Double>>> metricEntry : metricsMap.entrySet()) {
                String metric = metricEntry.getKey();
                MetricDirection direction = MetricDirection.forMetric(metric);
                Map<String, Map<Long, Double>> systemMap = metricEntry.getValue();

                for (Map.Entry<String, List<String>> entry : candidateToBaselines.entrySet()) {
                    String candidate = entry.getKey();
                    List<String> baselines = entry.getValue();

                    Map<Long, Double> candSeedMap = systemMap.get(candidate);
                    if (candSeedMap == null || candSeedMap.isEmpty()) {
                        continue;
                    }

                    // Pre-comparison container for a candidate's baseline family
                    List<IntermediateComparison> family = new ArrayList<>();

                    for (String baseline : baselines) {
                        if (candidate.equals(baseline)) {
                            continue;
                        }
                        Map<Long, Double> baseSeedMap = systemMap.get(baseline);
                        if (baseSeedMap == null || baseSeedMap.isEmpty()) {
                            continue;
                        }

                        // Identify common seeds sorted
                        Set<Long> commonSeeds = new TreeSet<>(candSeedMap.keySet());
                        commonSeeds.retainAll(baseSeedMap.keySet());
                        if (commonSeeds.isEmpty()) {
                            continue;
                        }

                        int n = commonSeeds.size();
                        double[] candVals = new double[n];
                        double[] baseVals = new double[n];
                        int idx = 0;
                        int wins = 0;
                        int losses = 0;
                        int ties = 0;

                        for (Long seed : commonSeeds) {
                            double cv = candSeedMap.get(seed);
                            double bv = baseSeedMap.get(seed);
                            candVals[idx] = cv;
                            baseVals[idx] = bv;
                            idx++;

                            if (Double.compare(cv, bv) == 0 || Math.abs(cv - bv) < 1e-12) {
                                ties++;
                            } else if (direction == MetricDirection.HIGHER_IS_BETTER) {
                                if (cv > bv) wins++;
                                else losses++;
                            } else { // LOWER_IS_BETTER
                                if (cv < bv) wins++;
                                else losses++;
                            }
                        }

                        double candMean = BootstrapConfidenceInterval.computeMean(candVals);
                        double baseMean = BootstrapConfidenceInterval.computeMean(baseVals);
                        double candMedian = BootstrapConfidenceInterval.computeMedian(candVals);
                        double baseMedian = BootstrapConfidenceInterval.computeMedian(baseVals);

                        if (n < MIN_PAIRED_SEEDS_FOR_INFERENCE) {
                            // Insufficient paired observations: report descriptive metrics only, skip inferential claims
                            family.add(new IntermediateComparison(
                                experiment, datasetType, model, candidate, baseline, metric, direction, n,
                                candMean, Double.NaN, Double.NaN, candMedian,
                                baseMean, Double.NaN, Double.NaN, baseMedian,
                                Double.NaN, Double.NaN, EffectSizeCalculator.Magnitude.NEGLIGIBLE,
                                Double.NaN, Double.NaN,
                                wins, losses, ties, ComparisonDecision.INSUFFICIENT_DATA
                            ));
                            continue;
                        }

                        // Bootstrap CIs
                        ConfidenceInterval candCi = BootstrapConfidenceInterval.bootstrapMean(candVals, bootstrapSeed);
                        ConfidenceInterval baseCi = BootstrapConfidenceInterval.bootstrapMean(baseVals, bootstrapSeed + 1L);

                        // Wilcoxon Test
                        WilcoxonResult wilcoxon = WilcoxonSignedRankTest.test(candVals, baseVals);

                        // Effect sizes
                        double hlShift = EffectSizeCalculator.hodgesLehmannShift(candVals, baseVals);
                        EffectSizeCalculator.CliffsDeltaResult cdResult = EffectSizeCalculator.cliffsDelta(candVals, baseVals);

                        family.add(new IntermediateComparison(
                            experiment, datasetType, model, candidate, baseline, metric, direction, n,
                            candCi.estimate(), candCi.lower(), candCi.upper(), candMedian,
                            baseCi.estimate(), baseCi.lower(), baseCi.upper(), baseMedian,
                            hlShift, cdResult.delta(), cdResult.magnitude(),
                            wilcoxon.statistic(), wilcoxon.pValue(),
                            wins, losses, ties, null
                        ));
                    }

                    if (family.isEmpty()) {
                        continue;
                    }

                    // Apply Holm correction only across valid inferential comparisons in this hypothesis family
                    List<Integer> validIndices = new ArrayList<>();
                    for (int i = 0; i < family.size(); i++) {
                        if (family.get(i).decision == null) {
                            validIndices.add(i);
                        }
                    }

                    double[] adjustedHolmPs = new double[family.size()];
                    Arrays.fill(adjustedHolmPs, Double.NaN);

                    if (!validIndices.isEmpty()) {
                        double[] rawPs = new double[validIndices.size()];
                        for (int k = 0; k < validIndices.size(); k++) {
                            rawPs[k] = family.get(validIndices.get(k)).rawPValue;
                        }
                        double[] holm = HolmCorrection.adjust(rawPs);
                        for (int k = 0; k < validIndices.size(); k++) {
                            adjustedHolmPs[validIndices.get(k)] = holm[k];
                        }
                    }

                    for (int i = 0; i < family.size(); i++) {
                        IntermediateComparison ic = family.get(i);
                        double holmP = adjustedHolmPs[i];

                        ComparisonDecision decision;
                        if (ic.decision != null) {
                            decision = ic.decision;
                        } else {
                            // Decision rule: p < alpha AND non-negligible effect
                            boolean nonNegligible = ic.cliffsMagnitude != EffectSizeCalculator.Magnitude.NEGLIGIBLE
                                || Math.abs(ic.hodgesLehmannShift) > 1e-6;

                            if (holmP < alpha && nonNegligible) {
                                boolean favorsCandidate = (ic.direction == MetricDirection.HIGHER_IS_BETTER && ic.hodgesLehmannShift > 0)
                                    || (ic.direction == MetricDirection.LOWER_IS_BETTER && ic.hodgesLehmannShift < 0);
                                decision = favorsCandidate ? ComparisonDecision.IMPROVEMENT : ComparisonDecision.DEGRADATION;
                            } else {
                                decision = ComparisonDecision.NO_MEANINGFUL_DIFFERENCE;
                            }
                        }

                        allComparisons.add(new StatisticalComparison(
                            ic.experiment, ic.datasetType, ic.responseModel, ic.candidateSystem, ic.baselineSystem,
                            ic.metric, ic.direction, ic.sampleSize,
                            ic.candidateMean, ic.candidateMeanCiLower, ic.candidateMeanCiUpper, ic.candidateMedian,
                            ic.baselineMean, ic.baselineMeanCiLower, ic.baselineMeanCiUpper, ic.baselineMedian,
                            ic.hodgesLehmannShift, ic.cliffsDelta, ic.cliffsMagnitude,
                            ic.wilcoxonStatistic, ic.rawPValue, holmP,
                            ic.wins, ic.losses, ic.ties, decision
                        ));
                    }
                }
            }
        }

        return allComparisons;
    }

    /**
     * Writes statistical comparisons to a CSV file.
     */
    public void writeCsv(List<StatisticalComparison> comparisons, Path csvPath) throws IOException {
        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvPath, StandardCharsets.UTF_8))) {
            writer.println(StatisticalComparison.csvHeader());
            for (StatisticalComparison sc : comparisons) {
                writer.println(sc.toCsvLine());
            }
        }
    }

    /**
     * Generates a GitHub-flavored Markdown evaluation report.
     */
    public String generateMarkdownReport(List<StatisticalComparison> comparisons) {
        StringBuilder sb = new StringBuilder();
        sb.append("# LifeLine Evaluation :: Statistical Comparison Report\n\n");
        sb.append("Pre-registered primary comparison per `docs/EVALUATION_PROTOCOL.md` Section 6.\n");
        sb.append("- Decision Rule: Improvement claimed only if Holm-adjusted p < 0.05 AND effect is non-negligible.\n");
        sb.append("- All pairwise tests are Paired Wilcoxon Signed-Rank across reporting seeds.\n\n");

        if (comparisons.isEmpty()) {
            sb.append("*No comparison data available.*\n");
            return sb.toString();
        }

        EvaluationDatasetType datasetType = comparisons.get(0).datasetType();
        switch (datasetType) {
            case FINAL_REPORTING -> {
                sb.append("> [!NOTE]\n");
                sb.append("> **PRE-REGISTERED PROTOCOL EVALUATION**: Completed on all 30 reporting seeds (1-30) strictly adhering to `docs/EVALUATION_PROTOCOL.md` Section 6.\n\n");
            }
            case INCOMPLETE_REPORTING -> {
                sb.append("> [!WARNING]\n");
                sb.append("> **INCOMPLETE REPORTING EVALUATION**: Dataset contains only ")
                    .append(comparisons.get(0).sampleSize())
                    .append(" of 30 reporting seeds (1-30). Per `docs/EVALUATION_PROTOCOL.md`, pre-registered research conclusions require the full suite of 30 reporting seeds.\n\n");
            }
            case EXPLORATORY_TUNING -> {
                sb.append("> [!NOTE]\n");
                sb.append("> **EXPLORATORY / TUNING EVALUATION**: Evaluated on tuning seeds (1000+). These results reflect exploratory parameter search / sensitivity analysis and must **NOT** be cited as pre-registered research conclusions.\n\n");
            }
        }

        boolean hasInsufficient = comparisons.stream()
            .anyMatch(sc -> sc.decision() == ComparisonDecision.INSUFFICIENT_DATA);
        if (hasInsufficient) {
            sb.append("> [!WARNING]\n");
            sb.append("> **INSUFFICIENT SAMPLE SIZE FOR HYPOTHESIS TESTING**: At least one comparison contains fewer than ")
                .append(MIN_PAIRED_SEEDS_FOR_INFERENCE)
                .append(" paired seeds. Exact two-sided Wilcoxon signed-rank significance at alpha = 0.05 is mathematically impossible with N < 6 ")
                .append("(minimum achievable p = 0.0625 at N = 5). Inferential tests and bootstrap confidence intervals have been omitted to prevent invalid significance claims.\n\n");
        }

        // Group by Model and Metric
        Map<String, Map<String, List<StatisticalComparison>>> grouped = new TreeMap<>();
        for (StatisticalComparison sc : comparisons) {
            grouped.computeIfAbsent(sc.responseModel(), k -> new TreeMap<>())
                .computeIfAbsent(sc.metric(), k -> new ArrayList<>())
                .add(sc);
        }

        for (Map.Entry<String, Map<String, List<StatisticalComparison>>> me : grouped.entrySet()) {
            String model = me.getKey();
            sb.append("## Response Model: ").append(model).append("\n\n");

            for (Map.Entry<String, List<StatisticalComparison>> te : me.getValue().entrySet()) {
                String metric = te.getKey();
                sb.append("### Metric: `").append(metric).append("`\n\n");

                sb.append("| Comparison | Candidate Mean [95% CI] | Baseline Mean [95% CI] | HL Shift | Cliff's Delta | Raw p | Holm p | W / L / T | Decision |\n");
                sb.append("|---|---|---|---|---|---|---|---|---|\n");

                for (StatisticalComparison sc : te.getValue()) {
                    String comp = sc.candidateSystem() + " vs " + sc.baselineSystem();
                    String candStr = Double.isNaN(sc.candidateMeanCiLower())
                        ? String.format(Locale.ROOT, "%.4f [unestimated]", sc.candidateMean())
                        : String.format(Locale.ROOT, "%.4f [%.4f, %.4f]", sc.candidateMean(), sc.candidateMeanCiLower(), sc.candidateMeanCiUpper());

                    String baseStr = Double.isNaN(sc.baselineMeanCiLower())
                        ? String.format(Locale.ROOT, "%.4f [unestimated]", sc.baselineMean())
                        : String.format(Locale.ROOT, "%.4f [%.4f, %.4f]", sc.baselineMean(), sc.baselineMeanCiLower(), sc.baselineMeanCiUpper());

                    String hlStr = Double.isNaN(sc.hodgesLehmannShift())
                        ? "N/A"
                        : String.format(Locale.ROOT, "%+.4f", sc.hodgesLehmannShift());

                    String cdStr = Double.isNaN(sc.cliffsDelta())
                        ? "N/A"
                        : String.format(Locale.ROOT, "%+.3f (%s)", sc.cliffsDelta(), sc.cliffsMagnitude());

                    String rawPStr = Double.isNaN(sc.rawPValue())
                        ? "N/A"
                        : String.format(Locale.ROOT, "%.4f", sc.rawPValue());

                    String holmPStr = Double.isNaN(sc.holmPValue())
                        ? "N/A"
                        : String.format(Locale.ROOT, "%.4f", sc.holmPValue());

                    String wlt = String.format("%d / %d / %d", sc.wins(), sc.losses(), sc.ties());

                    sb.append(String.format(Locale.ROOT,
                        "| %s | %s | %s | %s | %s | %s | %s | %s | **%s** |\n",
                        comp, candStr, baseStr, hlStr, cdStr, rawPStr, holmPStr, wlt, sc.decision()
                    ));
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Reads tidy evaluation rows from a results CSV file.
     */
    public static List<TidyMetricRow> loadTidyCsv(Path csvPath) throws IOException {
        List<TidyMetricRow> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null) return rows;

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                String[] parts = line.split(",");
                if (parts.length >= 6) {
                    rows.add(new TidyMetricRow(
                        parts[0].trim(),
                        parts[1].trim(),
                        parts[2].trim(),
                        Long.parseLong(parts[3].trim()),
                        parts[4].trim(),
                        Double.parseDouble(parts[5].trim())
                    ));
                }
            }
        }
        return rows;
    }

    public static StatisticalComparison comparePairedDerivedMetric(
        String experiment,
        EvaluationDatasetType datasetType,
        String responseModel,
        String candidateSystem,
        String baselineSystem,
        String metricName,
        MetricDirection direction,
        double[] derivedValues,
        double alpha,
        long bootstrapSeed
    ) {
        int n = derivedValues.length;
        double[] zeros = new double[n];
        int wins = 0;
        int losses = 0;
        int ties = 0;

        for (double v : derivedValues) {
            if (Double.compare(v, 0.0) == 0 || Math.abs(v) < 1e-12) {
                ties++;
            } else if (direction == MetricDirection.HIGHER_IS_BETTER) {
                if (v > 0.0) wins++;
                else losses++;
            } else {
                if (v < 0.0) wins++;
                else losses++;
            }
        }

        double candMean = BootstrapConfidenceInterval.computeMean(derivedValues);
        double candMedian = BootstrapConfidenceInterval.computeMedian(derivedValues);

        if (n < MIN_PAIRED_SEEDS_FOR_INFERENCE) {
            return new StatisticalComparison(
                experiment, datasetType, responseModel, candidateSystem, baselineSystem,
                metricName, direction, n,
                candMean, Double.NaN, Double.NaN, candMedian,
                0.0, Double.NaN, Double.NaN, 0.0,
                Double.NaN, Double.NaN, EffectSizeCalculator.Magnitude.NEGLIGIBLE,
                Double.NaN, Double.NaN, Double.NaN,
                wins, losses, ties, ComparisonDecision.INSUFFICIENT_DATA
            );
        }

        ConfidenceInterval candCi = BootstrapConfidenceInterval.bootstrapMean(derivedValues, bootstrapSeed);
        WilcoxonResult wilcoxon = WilcoxonSignedRankTest.test(derivedValues, zeros);
        double hlShift = EffectSizeCalculator.hodgesLehmannShift(derivedValues, zeros);
        EffectSizeCalculator.CliffsDeltaResult cdResult = EffectSizeCalculator.cliffsDelta(derivedValues, zeros);

        boolean nonNegligible = cdResult.magnitude() != EffectSizeCalculator.Magnitude.NEGLIGIBLE
            || Math.abs(hlShift) > 1e-6;

        ComparisonDecision decision;
        if (wilcoxon.pValue() < alpha && nonNegligible) {
            boolean favorsCandidate = (direction == MetricDirection.HIGHER_IS_BETTER && hlShift > 0)
                || (direction == MetricDirection.LOWER_IS_BETTER && hlShift < 0);
            decision = favorsCandidate ? ComparisonDecision.IMPROVEMENT : ComparisonDecision.DEGRADATION;
        } else {
            decision = ComparisonDecision.NO_MEANINGFUL_DIFFERENCE;
        }

        return new StatisticalComparison(
            experiment, datasetType, responseModel, candidateSystem, baselineSystem,
            metricName, direction, n,
            candCi.estimate(), candCi.lower(), candCi.upper(), candMedian,
            0.0, 0.0, 0.0, 0.0,
            hlShift, cdResult.delta(), cdResult.magnitude(),
            wilcoxon.statistic(), wilcoxon.pValue(), wilcoxon.pValue(),
            wins, losses, ties, decision
        );
    }

    private record IntermediateComparison(
        String experiment,
        EvaluationDatasetType datasetType,
        String responseModel,
        String candidateSystem,
        String baselineSystem,
        String metric,
        MetricDirection direction,
        int sampleSize,
        double candidateMean,
        double candidateMeanCiLower,
        double candidateMeanCiUpper,
        double candidateMedian,
        double baselineMean,
        double baselineMeanCiLower,
        double baselineMeanCiUpper,
        double baselineMedian,
        double hodgesLehmannShift,
        double cliffsDelta,
        EffectSizeCalculator.Magnitude cliffsMagnitude,
        double wilcoxonStatistic,
        double rawPValue,
        int wins,
        int losses,
        int ties,
        ComparisonDecision decision
    ) {
    }
}

