package co.edu.unbosque.biohub.consultapublica.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.biohub.consultapublica.soporte.Contenedores;

/** Las migraciones se aplican en un MySQL 8.4 real, de cero, y producen el esquema de 05-modelo-datos.md. */
@Testcontainers(disabledWithoutDocker = true)
class MigracionesIntegracionTest {

    private static String url;

    @BeforeAll
    static void migrar() throws SQLException {
        GenericContainer<?> mysql = Contenedores.mysql();
        String base = "mig_" + UUID.randomUUID().toString().replace("-", "");
        String servidor = "jdbc:mysql://" + mysql.getHost() + ":" + mysql.getMappedPort(3306) + "/";
        try (Connection c = DriverManager.getConnection(servidor, "root", "prueba"); Statement s = c.createStatement()) {
            s.execute("CREATE DATABASE " + base + " CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }
        url = servidor + base;
        Flyway.configure().dataSource(url, "root", "prueba").locations("classpath:db/migration").outOfOrder(true)
                .load().migrate();
    }

    private static List<String> consultar(String sql) throws SQLException {
        List<String> filas = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url, "root", "prueba"); Statement s = c.createStatement();
             ResultSet rs = s.executeQuery(sql)) {
            while (rs.next()) {
                filas.add(rs.getString(1));
            }
        }
        return filas;
    }

    private static void ejecutar(String sql) throws SQLException {
        try (Connection c = DriverManager.getConnection(url, "root", "prueba"); Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static String insertFicha(String id, int version, String estado, String depto) {
        return "INSERT INTO ficha_publica (hallazgo_id, version, nombre_cientifico, familia, fecha_recoleccion,"
                + " estado_conservacion, departamento_codigo, departamento_nombre, region_nombre) VALUES (UNHEX('"
                + id + "')," + version + ",'Atelopus x','Bufonidae','2026-03-14','" + estado + "','" + depto
                + "','Boyacá','Andina')";
    }

    @Test
    void esIdempotenteYValidaContraLoAplicado() {
        Flyway flyway = Flyway.configure().dataSource(url, "root", "prueba").locations("classpath:db/migration")
                .outOfOrder(true).load();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        flyway.validate();
    }

    @Test
    void creaTablasYCatalogoDe33Departamentos() throws SQLException {
        assertThat(consultar("SELECT table_name FROM information_schema.tables WHERE table_schema = DATABASE()"))
                .contains("departamento", "ficha_publica");
        assertThat(consultar("SELECT COUNT(*) FROM departamento")).containsExactly("33");
        assertThat(consultar("SELECT region FROM departamento WHERE codigo_dane = '15'")).containsExactly("Andina");
        assertThat(consultar("SELECT COUNT(DISTINCT region) FROM departamento")).containsExactly("6");
    }

    @Test
    void laFichaPublicaNoTieneColumnasDeCoordenadasNiDeUsuario() throws SQLException {
        List<String> columnas = consultar("SELECT LOWER(column_name) FROM information_schema.columns "
                + "WHERE table_schema = DATABASE() AND table_name = 'ficha_publica'");
        assertThat(columnas).contains("hallazgo_id", "version", "nombre_cientifico", "estado_conservacion");
        assertThat(columnas).noneMatch(c -> c.contains("lat") || c.contains("lon")
                || c.contains("coord") || c.contains("geo") || c.contains("usuario") || c.contains("investigador"));
    }

    @Test
    void tieneElIndiceFulltextYLosIndicesDeFiltro() throws SQLException {
        assertThat(consultar("SELECT DISTINCT index_name FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'ficha_publica' AND index_type = 'FULLTEXT'"))
                .containsExactly("ft_ficha_publica_nombres");
        assertThat(consultar("SELECT DISTINCT index_name FROM information_schema.statistics "
                + "WHERE table_schema = DATABASE() AND table_name = 'ficha_publica'"))
                .contains("idx_ficha_publica_nombre", "idx_ficha_publica_familia_nombre",
                        "idx_ficha_publica_depto_nombre");
    }

    @Test
    void lasRestriccionesRechazanDatosInvalidos() throws SQLException {
        String id = UUID.randomUUID().toString().replace("-", "");
        ejecutar(insertFicha(id, 1, "EN_PELIGRO", "15")); // válida
        assertThatThrownBy(() -> ejecutar(insertFicha(id, 1, "EN_PELIGRO", "15"))).as("PK duplicada")
                .isInstanceOf(SQLException.class);
        String otro = UUID.randomUUID().toString().replace("-", "");
        assertThatThrownBy(() -> ejecutar(insertFicha(otro, 1, "CRITICO", "15"))).as("estado inválido")
                .hasMessageContaining("ck_ficha_publica_estado");
        assertThatThrownBy(() -> ejecutar(insertFicha(otro, 0, "EN_PELIGRO", "15"))).as("versión 0")
                .hasMessageContaining("ck_ficha_publica_version");
        assertThatThrownBy(() -> ejecutar(insertFicha(otro, 1, "EN_PELIGRO", "00"))).as("departamento inexistente")
                .hasMessageContaining("fk_ficha_publica_departamento");
    }
}
