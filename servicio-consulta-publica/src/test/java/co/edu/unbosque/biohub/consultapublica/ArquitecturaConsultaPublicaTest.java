package co.edu.unbosque.biohub.consultapublica;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/** ARQ-18 (SAD §7.1): service, infrastructure, config, eventos y domain no dependen de controller. */
class ArquitecturaConsultaPublicaTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("co.edu.unbosque.biohub.consultapublica");

    @Test
    @DisplayName("ARQ-18: service, infrastructure, domain y config no importan controller")
    void capasInternasNoImportanController() {
        noClasses().that().resideInAnyPackage("..consultapublica.service..", "..consultapublica.infrastructure..",
                        "..consultapublica.domain..", "..consultapublica.config..")
                .should().dependOnClassesThat().resideInAPackage("..consultapublica.controller..").check(CLASES);
    }

    @Test
    @DisplayName("ARQ-18: el módulo eventos (service/domain) tampoco importa controller de la consulta de fichas")
    void eventosNoImportaElControllerDeFichas() {
        noClasses().that().resideInAnyPackage("..consultapublica.eventos.service..", "..consultapublica.eventos.domain..")
                .should().dependOnClassesThat().resideInAPackage("..consultapublica.controller..").check(CLASES);
    }

    // ---- ARQ-28 (SAD §7.1): las dependencias apuntan hacia domain ----

    @Test
    @DisplayName("ARQ-28: layeredArchitecture; domain no depende de nadie y controller no lo importa ninguna capa")
    void arquitecturaEnCapas() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy("..consultapublica.controller..")
                .layer("Service").definedBy("..consultapublica.service..")
                .layer("Infrastructure").definedBy("..consultapublica.infrastructure..")
                .layer("Domain").definedBy("..consultapublica.domain..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Domain").mayNotAccessAnyLayer()
                .check(CLASES);
    }

    @Test
    @DisplayName("ARQ-28: service no depende de config (MonitorCarga recibe solo UmbralesDegradacion) ni de controller")
    void serviceNoDependeDeConfig() {
        noClasses().that().resideInAPackage("..consultapublica.service..")
                .should().dependOnClassesThat().resideInAnyPackage("..consultapublica.config..", "..consultapublica.controller..")
                .check(CLASES);
    }

    @Test
    @DisplayName("ARQ-28: domain no depende de service, infrastructure, config ni controller")
    void domainNoDependeDeLasCapasExternas() {
        noClasses().that().resideInAPackage("..consultapublica.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..consultapublica.service..",
                        "..consultapublica.infrastructure..", "..consultapublica.config..", "..consultapublica.controller..")
                .check(CLASES);
    }

    /**
     * Deuda ARQ-28 acotada: {@code BusquedaFichasJdbc} devuelve DTO de {@code service.vista} y recibe
     * {@code CriterioBusquedaPublica}. Esta regla congela ese caso y falla ante cualquier otra dependencia de
     * infrastructure hacia service.
     */
    @Test
    @DisplayName("ARQ-28: infrastructure no depende de service, salvo la deuda documentada (CriterioBusquedaPublica y service.vista)")
    void infrastructureNoDependeDeService() {
        noClasses().that().resideInAPackage("..consultapublica.infrastructure..")
                .should().dependOnClassesThat(resideInAPackage("..consultapublica.service..")
                        .and(not(resideInAPackage("..consultapublica.service.vista..")))
                        .and(not(simpleName("CriterioBusquedaPublica"))))
                .check(CLASES);
    }
}
