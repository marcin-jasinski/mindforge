package dev.mindforge.unit.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import dev.mindforge.application.service.RunWorker;
import dev.mindforge.domain.port.IngestRunRepository;
import org.springframework.web.bind.annotation.RestController;

@AnalyzeClasses(packages = "dev.mindforge", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_depends_only_on_the_jdk = classes()
        .that().resideInAPackage("dev.mindforge.domain..")
        .should().onlyDependOnClassesThat().resideInAnyPackage("java..", "dev.mindforge.domain..")
        .because("the domain layer imports the JDK only - zero I/O, zero framework"
            + " (CLAUDE.md § Architecture, docs/standards/architecture/hexagonal.md)");

    @ArchTest
    static final ArchRule application_depends_only_on_domain_agent_tx_and_slf4j = classes()
        .that().resideInAPackage("dev.mindforge.application..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "dev.mindforge.domain..",
            "dev.mindforge.application..",
            "dev.mindforge.agent..",
            "org.springframework.transaction..",
            "org.slf4j..")
        .because("the application layer imports the domain, the model services, Spring's transaction API"
            + " and the SLF4J facade only (CLAUDE.md § Architecture, docs/standards/architecture/hexagonal.md)");

    @ArchTest
    static final ArchRule model_services_depend_only_on_domain_and_infrastructure_ai = classes()
        .that().resideInAPackage("dev.mindforge.agent..")
        .should().onlyDependOnClassesThat().resideInAnyPackage(
            "java..",
            "dev.mindforge.domain..",
            "dev.mindforge.agent..",
            "dev.mindforge.infrastructure.ai..")
        .because("model services import the domain and infrastructure.ai only"
            + " (CLAUDE.md § Architecture, docs/standards/architecture/hexagonal.md)");

    @ArchTest
    static final ArchRule inner_layers_do_not_depend_on_adapters = noClasses()
        .that().resideInAnyPackage(
            "dev.mindforge.domain..",
            "dev.mindforge.application..",
            "dev.mindforge.agent..",
            "dev.mindforge.infrastructure..")
        .should().dependOnClassesThat().resideInAnyPackage(
            "dev.mindforge.api..",
            "dev.mindforge.cli..",
            "dev.mindforge.discord..",
            "dev.mindforge.slack..")
        .because("dependencies point inward: adapters depend on inner layers, never the reverse"
            + " (CLAUDE.md § Architecture, docs/standards/architecture/hexagonal.md)");

    @ArchTest
    static final ArchRule controllers_do_not_access_ports_or_persistence_directly = noClasses()
        .that().areAnnotatedWith(RestController.class)
        .should().dependOnClassesThat().resideInAnyPackage(
            "dev.mindforge.domain.port..",
            "dev.mindforge.infrastructure.persistence..")
        .because("controllers are thin and reach data only through dev.mindforge.application services"
            + " (CLAUDE.md § API / Controllers, docs/standards/backend/api.md)");

    @ArchTest
    static final ArchRule model_services_do_not_depend_on_other_model_services = classes()
        .that().resideInAPackage("dev.mindforge.agent..")
        .should(notDependOnAnotherTopLevelClassIn("dev.mindforge.agent"))
        .because("model services are concrete and independent; the application layer sequences them"
            + " (CLAUDE.md § Architecture, docs/standards/backend/ai_agents.md)");

    @ArchTest
    static final ArchRule only_run_worker_reads_the_run_queue = noClasses()
        .that().doNotHaveFullyQualifiedName(RunWorker.class.getName())
        .should().callMethod(IngestRunRepository.class, "findUnfinished")
        .orShould().callMethod(IngestRunRepository.class, "knowledgeBasesWithQueuedRuns")
        .because("the cross-tenant queue reads are for the RunWorker sweep only"
            + " (T25, docs/wayfinder/tickets/25-port-read-surface.md)");

    private static ArchCondition<JavaClass> notDependOnAnotherTopLevelClassIn(String packageName) {
        return new ArchCondition<>("not depend on another top-level class in " + packageName) {
            @Override
            public void check(JavaClass origin, ConditionEvents events) {
                JavaClass originOwner = topLevelOwner(origin);
                for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    boolean inPackage = target.getPackageName().equals(packageName)
                        || target.getPackageName().startsWith(packageName + ".");
                    if (inPackage && !topLevelOwner(target).equals(originOwner)) {
                        events.add(SimpleConditionEvent.violated(dependency, dependency.getDescription()));
                    }
                }
            }
        };
    }

    private static JavaClass topLevelOwner(JavaClass javaClass) {
        JavaClass owner = javaClass;
        while (owner.getEnclosingClass().isPresent()) {
            owner = owner.getEnclosingClass().get();
        }
        return owner;
    }
}
