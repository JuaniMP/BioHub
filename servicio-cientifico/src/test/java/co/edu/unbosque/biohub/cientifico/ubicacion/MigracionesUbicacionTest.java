package co.edu.unbosque.biohub.cientifico.ubicacion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;

/** Las migraciones validan contra Hibernate (ddl-auto: validate) en un MySQL 8.4 real. */
class MigracionesUbicacionTest extends BaseUbicacionMySqlTest {

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Test
    void hibernateValidaElEsquemaCreadoPorFlyway() {
        // Si las entidades no coincidieran con las migraciones, el contexto no habría arrancado.
        assertThat(ddlAuto).isEqualTo("validate");
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
        assertThat(versiones).contains("20261026.1000", "20261026.1010");
    }

    @Test
    void elCatalogoTiene33DepartamentosY6Regiones() {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_departamento", Integer.class)).isEqualTo(33);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_region", Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_departamento d JOIN ubicacion_region r ON r.id = d.region_id WHERE d.codigo_dane = '15' AND r.nombre = 'Andina'", Integer.class)).isEqualTo(1);
    }

    @Test
    void noExistenColumnasDeCoordenadasEnClaro() {
        List<String> columnas = jdbc.queryForList(
                "SELECT LOWER(column_name) FROM information_schema.columns WHERE table_schema = DATABASE()"
                        + " AND table_name = 'ubicacion_hallazgo'", String.class);
        assertThat(columnas).contains("coord_cifrada", "coord_nonce", "clave_version", "cifrado_en", "eliminado_en");
        assertThat(columnas).noneMatch(c -> c.contains("lat") && !c.equals("clave_version")
                || c.contains("lon") || c.contains("lng"));
    }

    @Test
    void lasCuatroColumnasDeCifradoVanJuntas() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ubicacion_hallazgo (hallazgo_id, version, departamento_codigo,"
                + " nombre_cientifico, fecha_recoleccion, investigador, estado_conservacion, coord_cifrada)"
                + " VALUES (UUID_TO_BIN(UUID()), 1, '15', 'X y', '2026-01-01', 'i', 'EN_PELIGRO', RANDOM_BYTES(32))"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO ubicacion_hallazgo (hallazgo_id, version, departamento_codigo,"
                + " nombre_cientifico, fecha_recoleccion, investigador, estado_conservacion)"
                + " VALUES (UUID_TO_BIN(UUID()), 1, '15', 'X y', '2026-01-01', 'i', 'INVENTADO')"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void elParNonceYLlaveNoSePuedeRepetir() {
        String insert = "INSERT INTO ubicacion_hallazgo (hallazgo_id, version, departamento_codigo, nombre_cientifico,"
                + " fecha_recoleccion, investigador, estado_conservacion, coord_cifrada, coord_nonce, clave_version, cifrado_en)"
                + " VALUES (UUID_TO_BIN(UUID()), 1, '15', 'X y', '2026-01-01', 'i', 'EN_PELIGRO', RANDOM_BYTES(32),"
                + " UNHEX('000000000000000000000001'), 'v1', NOW(3))";
        jdbc.update(insert);
        assertThatThrownBy(() -> jdbc.update(insert)).isInstanceOf(DataAccessException.class);
    }

    /** 05 §13: las migraciones solo insertan los catálogos de referencia, nunca datos de prueba. */
    @Test
    void lasMigracionesSoloInsertanCatalogos() throws IOException {
        Path carpeta = Path.of("src/main/resources/db/migration");
        Pattern insert = Pattern.compile("(?i)INSERT\\s+INTO\\s+([a-z_]+)");
        try (Stream<Path> archivos = Files.list(carpeta)) {
            for (Path archivo : archivos.filter(p -> p.getFileName().toString().startsWith("V2026")).toList()) {
                Matcher m = insert.matcher(Files.readString(archivo));
                while (m.find()) {
                    assertThat(m.group(1)).isIn("ubicacion_region", "ubicacion_departamento");
                }
            }
        }
    }
}
