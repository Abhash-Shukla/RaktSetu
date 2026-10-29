package lifeline.arch;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Enforces architectural module boundaries as specified in AGENTS.md Section 2.
 * core and api must never depend on sim or eval.
 * core must never depend on Spring.
 */
@AnalyzeClasses(packages = "lifeline", importOptions = {ImportOption.DoNotIncludeTests.class})
public class ModuleBoundaryTest {

    @ArchTest
    static final ArchRule coreAndApiMustNotDependOnSimOrEval =
        noClasses()
            .that().resideInAnyPackage("lifeline.core..", "lifeline.api..")
            .should().dependOnClassesThat().resideInAnyPackage("lifeline.sim..", "lifeline.eval..")
            .because("AGENTS.md section 2 strictly prohibits core and api from importing or depending on sim or eval");

    @ArchTest
    static final ArchRule coreMustNotDependOnSpring =
        noClasses()
            .that().resideInAPackage("lifeline.core..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..")
            .because("AGENTS.md section 2 requires lifeline-core to be pure Java with no Spring dependencies");
}
