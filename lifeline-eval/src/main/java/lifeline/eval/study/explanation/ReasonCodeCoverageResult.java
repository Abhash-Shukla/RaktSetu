package lifeline.eval.study.explanation;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Result metrics for engine reason-code coverage and explanation representation (Claim C5 / E7).
 *
 * @param totalCatalogCodes total number of distinct reason codes defined in the engine catalog
 * @param coveredCodes set of distinct reason codes observed during the evaluation
 * @param coverageRate fraction of engine reason codes covered (coveredCodes.size() / totalCatalogCodes)
 * @param occurrencesPerCode mapping of reason code to number of times observed
 * @param unsupportedCodesFound list of any codes emitted by factors not registered in the catalog
 * @param semanticVerificationErrors list of discrepancies between factor reason codes and explanation outputs
 * @param passed true if coverageRate == 1.0, unsupportedCodesFound is empty, and semanticVerificationErrors is empty
 */
public record ReasonCodeCoverageResult(
    int totalCatalogCodes,
    Set<String> coveredCodes,
    double coverageRate,
    Map<String, Long> occurrencesPerCode,
    List<String> unsupportedCodesFound,
    List<String> semanticVerificationErrors,
    boolean passed
) {}

