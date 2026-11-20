package lifeline.eval.study.explanation;

import lifeline.core.domain.BloodGroup;
import lifeline.core.domain.Urgency;
import lifeline.core.factor.FactorContribution;

import java.util.List;

/**
 * Structured model representing a reproducible sample explanation generated from actual engine output.
 * Suitable for inclusion in research paper figures, tables, and audit reports.
 */
public record SampleExplanation(
    String requestId,
    BloodGroup requestBloodGroup,
    Urgency requestUrgency,
    String requestZone,
    List<SampleRankedDonor> rankedDonors
) {
    public record SampleRankedDonor(
        int rank,
        String donorId,
        BloodGroup bloodGroup,
        String homeZone,
        String currentZone,
        boolean isStaleLocation,
        double totalScore,
        List<FactorContribution> contributions,
        String topContributingFactor,
        boolean staleLocationFlag,
        boolean rareTypeConservedFlag,
        String explanationSummary
    ) {}

    /**
     * Formats the sample explanation as a GitHub-flavored Markdown artifact.
     */
    public String toMarkdown() {
        StringBuilder sb = new StringBuilder();
        sb.append("### Sample Match Explanation Report\n\n");
        sb.append(String.format("- **Request ID**: `%s`\n", requestId));
        sb.append(String.format("- **Blood Requirement**: **%s** (%s)\n", requestBloodGroup, requestUrgency));
        sb.append(String.format("- **Destination Hospital Zone**: `%s`\n\n", requestZone));
        sb.append("#### Ranked Candidates and Traceable Factor Breakdown\n\n");
        sb.append("| Rank | Donor ID | Group | Zone | Score | Top Factor | Flags | Explanation Summary |\n");
        sb.append("|---|---|---|---|---|---|---|---|\n");

        for (SampleRankedDonor d : rankedDonors) {
            String flags = String.format("%s%s",
                d.staleLocationFlag() ? "[STALE_LOC] " : "",
                d.rareTypeConservedFlag() ? "[RARE_CONSERVED] " : ""
            ).trim();
            if (flags.isEmpty()) {
                flags = "NONE";
            }

            sb.append(String.format("| #%d | `%s` | %s | %s | %.4f | %s | %s | %s |\n",
                d.rank(), d.donorId(), d.bloodGroup(),
                d.currentZone() != null ? d.currentZone() : d.homeZone(),
                d.totalScore(), d.topContributingFactor(), flags, d.explanationSummary()
            ));
        }

        sb.append("\n#### Detailed Factor Breakdown per Candidate\n\n");
        for (SampleRankedDonor d : rankedDonors) {
            sb.append(String.format("##### Candidate #%d (`%s` - %s)\n", d.rank(), d.donorId(), d.bloodGroup()));
            sb.append("- **Total Score**: `").append(String.format("%.6f", d.totalScore())).append("`\n");
            sb.append("- **Explanation**: _\"").append(d.explanationSummary()).append("\"_\n");
            sb.append("- **Factor Contributions**:\n");
            sb.append("  | Factor | Raw Value | Weight | Weighted Contribution | Reason Code |\n");
            sb.append("  |---|---|---|---|---|\n");
            for (FactorContribution fc : d.contributions()) {
                sb.append(String.format("  | `%s` | %+.4f | %.2f | %+.4f | `%s` |\n",
                    fc.factorName(), fc.rawValue(), fc.weight(), fc.contribution(), fc.reasonCode()
                ));
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}

