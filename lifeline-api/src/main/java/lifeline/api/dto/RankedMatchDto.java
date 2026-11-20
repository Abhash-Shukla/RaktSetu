package lifeline.api.dto;

import java.util.Map;

public record RankedMatchDto(
    int rankPosition,
    Long donorId,
    String bloodGroup,
    String zone,
    double totalScore,
    String plainLanguageExplanation,
    String reasonCode,
    Map<String, Double> factorContributions
) {}
