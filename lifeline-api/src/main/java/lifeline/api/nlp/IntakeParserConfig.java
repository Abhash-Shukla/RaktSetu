package lifeline.api.nlp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration for IntakeParser port.
 * Allows switching between deterministic fake (default for offline testing) and Gemini LLM adapter.
 */
@Configuration
public class IntakeParserConfig {

    private static final Logger log = LoggerFactory.getLogger(IntakeParserConfig.class);

    @Bean
    @ConditionalOnProperty(name = "lifeline.nlp.provider", havingValue = "fake", matchIfMissing = true)
    public IntakeParser fakeIntakeParser(
        PromptInjectionGuard promptInjectionGuard,
        IntakeSchemaValidator schemaValidator
    ) {
        log.info("Initialized DeterministicFakeIntakeParser (offline deterministic mode)");
        return new DeterministicFakeIntakeParser(promptInjectionGuard, schemaValidator);
    }

    @Bean
    @ConditionalOnProperty(name = "lifeline.nlp.provider", havingValue = "gemini")
    public IntakeParser geminiIntakeParser(
        @Value("${lifeline.nlp.api-key:${GEMINI_API_KEY:}}") String apiKey,
        @Value("${lifeline.nlp.model:gemini-1.5-flash}") String model,
        @Value("${lifeline.nlp.timeout-ms:5000}") long timeoutMs,
        PromptInjectionGuard promptInjectionGuard,
        IntakeSchemaValidator schemaValidator
    ) {
        log.info("Initialized GeminiIntakeParser (model: {}, timeout: {}ms)", model, timeoutMs);
        return new GeminiIntakeParser(apiKey, model, timeoutMs, promptInjectionGuard, schemaValidator);
    }
}
