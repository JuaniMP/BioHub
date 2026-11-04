package co.edu.unbosque.biohub.consultapublica;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

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
}
