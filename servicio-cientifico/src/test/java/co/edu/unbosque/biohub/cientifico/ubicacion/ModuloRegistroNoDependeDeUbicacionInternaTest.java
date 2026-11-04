package co.edu.unbosque.biohub.cientifico.ubicacion;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * D45 / ARQ-29 (03 §J2.1, SAD §2.5 y §7.1): el módulo {@code registro} NO depende de nada de
 * {@code co.edu.unbosque.biohub.cientifico.ubicacion} (ni siquiera de {@code UbicacionService}). Registro y ubicacion
 * se integran solo por los eventos de dominio de {@code cientifico.eventos.domain}
 * ({@code HallazgoRegistrado}/{@code HallazgoReclasificado}, publicados con {@code ApplicationEventPublisher}).
 * Dos comprobaciones: sobre el código fuente (atrapa también importaciones sin clases compiladas) y sobre el
 * bytecode con ArchUnit.
 */
class ModuloRegistroNoDependeDeUbicacionInternaTest {

    private static final String UBICACION = "co.edu.unbosque.biohub.cientifico.ubicacion";

    @Test
    @DisplayName("D45: ningun archivo de registro menciona el paquete cientifico.ubicacion")
    void registroNoImportaNadaDeUbicacion() throws IOException {
        Path registro = Path.of("src/main/java/co/edu/unbosque/biohub/cientifico/registro");
        try (Stream<Path> archivos = Files.walk(registro)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                assertThat(Files.readString(archivo)).as(archivo.toString()).doesNotContain(UBICACION);
            }
        }
    }

    @Test
    @DisplayName("D45 ARQ-29: ArchUnit, registro no depende de ninguna clase de cientifico.ubicacion")
    void registroNoDependeDeUbicacionEnElBytecode() {
        JavaClasses clases = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("co.edu.unbosque.biohub.cientifico");
        noClasses().that().resideInAPackage("..cientifico.registro..")
                .should().dependOnClassesThat().resideInAPackage(UBICACION + "..")
                .allowEmptyShould(true) // hoy registro solo tiene package-info: la regla vale cuando Hernando agregue clases
                .check(clases);
    }
}
