package co.edu.unbosque.biohub.cientifico;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 11-seguridad-y-acceso.md §3: todo endpoint declara su rol. Un método de un {@code @RestController} con
 * {@code @GetMapping}, {@code @PostMapping}, {@code @PutMapping}, {@code @PatchMapping}, {@code @DeleteMapping} o
 * {@code @RequestMapping} sin {@code @PreAuthorize} rompe el build (también los de {@code registro}).
 * Para un endpoint público se escribe {@code @PreAuthorize("permitAll()")} de forma explícita.
 */
class ArquitecturaSeguridadTest {

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("co.edu.unbosque.biohub.cientifico");

    // GetMapping, PostMapping, etc. llevan @RequestMapping como meta-anotación
    private static ArchRule reglaTodoEndpointTienePreAuthorize() {
        return methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .and(CanBeAnnotated.Predicates.metaAnnotatedWith(RequestMapping.class)
                        .or(CanBeAnnotated.Predicates.annotatedWith(RequestMapping.class)))
                .should().beAnnotatedWith(PreAuthorize.class)
                .because("cada endpoint debe declarar su rol (o permitAll() si es público)")
                .allowEmptyShould(true);
    }

    @Test
    @DisplayName("SEG: todo método de endpoint de un @RestController tiene @PreAuthorize")
    void todoEndpointTienePreAuthorize() {
        reglaTodoEndpointTienePreAuthorize().check(CLASES);
    }

    @RestController
    @RequestMapping("/api/cientifico/fixture")
    static class ControladorSinRol {
        @GetMapping("/sin-rol")
        public String sinRol() {
            return "x";
        }

        @PostMapping("/con-rol")
        @PreAuthorize("hasRole('administrador')")
        public String conRol() {
            return "x";
        }
    }

    @Test
    @DisplayName("SEG: la regla detecta un endpoint sin @PreAuthorize (control de la propia regla)")
    void laReglaFallaSiFaltaElRol() {
        JavaClasses fixture = new ClassFileImporter().importClasses(ControladorSinRol.class);
        assertThatThrownBy(() -> reglaTodoEndpointTienePreAuthorize().check(fixture))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("sinRol");
    }
}
