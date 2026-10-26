package co.edu.unbosque.biohub.cientifico.ubicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** Regla de módulos (03 §J2.1): registro solo importa los tipos públicos de {@code ubicacion.service} (UbicacionService y comandos). */
class ModuloRegistroNoDependeDeUbicacionInternaTest {

    private static final List<String> PROHIBIDOS = List.of(
            "co.edu.unbosque.biohub.cientifico.ubicacion.domain", "co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure", "co.edu.unbosque.biohub.cientifico.ubicacion.controller");
    private static final List<String> INTERNOS_DEL_SERVICIO = List.of(
            "ServicioDescifrado", "PoliticaAccesoUbicacion", "BusquedaHallazgosService", "MapaService",
            "RecifradoService", "CatalogoDepartamentos", "AuditoriaAccesoUbicacion");

    @Test
    void registroNoImportaClasesInternasDeUbicacion() throws IOException {
        Path registro = Path.of("src/main/java/co/edu/unbosque/biohub/cientifico/registro");
        try (Stream<Path> archivos = Files.walk(registro)) {
            for (Path archivo : archivos.filter(p -> p.toString().endsWith(".java")).toList()) {
                String fuente = Files.readString(archivo);
                for (String paquete : PROHIBIDOS) {
                    assertThat(fuente).as(archivo.toString()).doesNotContain(paquete);
                }
                for (String clase : INTERNOS_DEL_SERVICIO) {
                    assertThat(fuente).as(archivo.toString()).doesNotContain("ubicacion.service." + clase);
                }
            }
        }
    }
}
