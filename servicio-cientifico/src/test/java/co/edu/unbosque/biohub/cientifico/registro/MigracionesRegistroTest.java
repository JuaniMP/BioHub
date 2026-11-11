package co.edu.unbosque.biohub.cientifico.registro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;

/**
 * Comprueba contra un MySQL real que el esquema de registro es el acordado y que las garantías que el
 * SAD atribuye a la base de datos están de verdad en la base de datos, no solo en el código Java.
 */
class MigracionesRegistroTest extends BaseRegistroMySqlTest {

    private static final String USUARIO_APP = "app_restringido";
    private static final String CLAVE_APP = "Prueba1234";

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Test
    @DisplayName("Hibernate valida el esquema que creo Flyway")
    void hibernateValidaElEsquema() {
        assertThat(ddlAuto).isEqualTo("validate");
        List<String> versiones = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success = 1 ORDER BY installed_rank", String.class);
        assertThat(versiones).contains("20261104.1000");
    }

    @Test
    @DisplayName("el id del hallazgo es BINARY(16), como acordamos con ubicacion (doc 05 seccion 6)")
    void elIdEsBinario16() {
        String tipo = jdbc.queryForObject(
                "SELECT column_type FROM information_schema.columns WHERE table_schema = DATABASE()"
                        + " AND table_name = 'hallazgo' AND column_name = 'id'", String.class);
        assertThat(tipo).isEqualToIgnoringCase("binary(16)");
    }

    @Test
    @DisplayName("ninguna tabla de registro guarda coordenadas (D14, RNF9)")
    void ningunaColumnaDeCoordenadas() {
        List<String> columnas = jdbc.queryForList(
                "SELECT LOWER(CONCAT(table_name, '.', column_name)) FROM information_schema.columns"
                        + " WHERE table_schema = DATABASE() AND table_name IN"
                        + " ('hallazgo','hallazgo_version','hallazgo_auditoria','hallazgo_evidencia','ficha_plantilla')",
                String.class);
        assertThat(columnas).noneMatch(c -> {
            String campo = c.substring(c.indexOf('.') + 1);
            return campo.contains("latitud") || campo.contains("longitud") || campo.equals("lat")
                    || campo.equals("lon") || campo.equals("lng") || campo.contains("coordenada");
        });
    }

    @Test
    @DisplayName("una reclasificacion sin justificacion la rechaza la propia base de datos (HU-03)")
    void laBaseExigeJustificacionDesdeLaVersion2() {
        insertarHallazgo();
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO hallazgo_version (id, hallazgo_id, version, snapshot, autor)"
                        + " VALUES (UUID_TO_BIN(UUID()), @h, 2, '{\"a\":1}', 'cientifico1')"))
                .isInstanceOf(DataAccessException.class);
    }

    /**
     * HU-05, criterio 2: "bloquear fisicamente cualquier UPDATE o DELETE sobre las tablas de auditoria".
     *
     * <p>La via de los triggers quedo descartada: MySQL con binary logging exige SUPER para crearlos
     * (ERROR 1419) y ningun usuario del proyecto lo tiene. La unica via disponible son los permisos,
     * y esta prueba verifica que funciona: crea un usuario con el reparto que propone
     * {@code deploy/aiven/03-registro-historial-append-only.sql.example} y comprueba que con esa
     * credencial el historial solo admite altas y lecturas.
     *
     * <p>Mientras el equipo no aplique ese script, la garantia depende del codigo y de los CHECK.
     */
    @Test
    @DisplayName("con los permisos propuestos, el historial solo admite INSERT y SELECT (HU-05, criterio 2)")
    void conLosPermisosPropuestosElHistorialEsAppendOnly() throws SQLException {
        insertarHallazgo();
        jdbc.update("INSERT INTO hallazgo_version (id, hallazgo_id, version, snapshot, autor)"
                + " VALUES (UUID_TO_BIN(UUID()), @h, 1, '{\"version\":1}', 'investigador1')");
        jdbc.update("INSERT INTO hallazgo_auditoria (hallazgo_id, version_previa, version_nueva, campo,"
                + " valor_previo, valor_nuevo, justificacion, autor)"
                + " VALUES (@h, 1, 2, 'estadoConservacion', 'NO_AMENAZADA', 'EN_PELIGRO', 'Revision', 'cientifico1')");

        crearUsuarioConPermisosPropuestos();
        try (Connection app = DriverManager.getConnection(MYSQL.getJdbcUrl(), USUARIO_APP, CLAVE_APP);
             Statement sentencia = app.createStatement()) {

            // Lo que debe seguir funcionando
            assertThatCode(() -> sentencia.executeQuery("SELECT COUNT(*) FROM hallazgo_version"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> sentencia.executeUpdate(
                    "INSERT INTO hallazgo_auditoria (hallazgo_id, version_nueva, campo, autor)"
                            + " SELECT id, 3, 'nota', 'cientifico1' FROM hallazgo LIMIT 1"))
                    .doesNotThrowAnyException();

            // Lo que debe quedar bloqueado
            assertThatThrownBy(() -> sentencia.executeUpdate("UPDATE hallazgo_version SET autor = 'otro'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> sentencia.executeUpdate("DELETE FROM hallazgo_version"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> sentencia.executeUpdate("UPDATE hallazgo_auditoria SET valor_nuevo = 'x'"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");
            assertThatThrownBy(() -> sentencia.executeUpdate("DELETE FROM hallazgo_auditoria"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("denied");

            // Y el hallazgo si se puede corregir: la restriccion es solo del historial
            assertThatCode(() -> sentencia.executeUpdate("UPDATE hallazgo SET nombre_comun = 'otro'"))
                    .doesNotThrowAnyException();
        }
    }

    /**
     * Reproduce el reparto de permisos de deploy/aiven/03-registro-historial-append-only.sql.example.
     * Se conecta como root porque crear usuarios y otorgar permisos no esta al alcance del usuario
     * con el que corre la aplicacion, que es justamente lo que se quiere demostrar.
     */
    private void crearUsuarioConPermisosPropuestos() throws SQLException {
        String base = MYSQL.getDatabaseName();
        try (Connection root = DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
             Statement ddl = root.createStatement()) {
            ddl.execute("DROP USER IF EXISTS '" + USUARIO_APP + "'@'%'");
            ddl.execute("CREATE USER '" + USUARIO_APP + "'@'%' IDENTIFIED BY '" + CLAVE_APP + "'");
            // 1. De base solo lectura y altas
            ddl.execute("GRANT SELECT, INSERT ON `" + base + "`.* TO '" + USUARIO_APP + "'@'%'");
            // 2. UPDATE y DELETE se devuelven tabla por tabla, menos en las dos del historial
            for (String tabla : List.of("hallazgo", "hallazgo_evidencia", "ficha_plantilla", "ubicacion_hallazgo")) {
                ddl.execute("GRANT UPDATE, DELETE ON `" + base + "`.`" + tabla + "` TO '" + USUARIO_APP + "'@'%'");
            }
            ddl.execute("FLUSH PRIVILEGES");
        }
    }

    @Test
    @DisplayName("un estado de conservacion inventado lo rechaza la base de datos")
    void soloLosTresEstadosAcordados() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO hallazgo (id, version, nombre_cientifico, familia, fecha_recoleccion,"
                        + " investigador, departamento, estado_conservacion)"
                        + " VALUES (UUID_TO_BIN(UUID()), 1, 'X y', 'F', '2026-01-01', 'i', 'Boyaca', 'INVENTADO')"))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    @DisplayName("no se puede repetir la misma version de un hallazgo")
    void versionUnicaPorHallazgo() {
        insertarHallazgo();
        jdbc.update("INSERT INTO hallazgo_version (id, hallazgo_id, version, snapshot, autor)"
                + " VALUES (UUID_TO_BIN(UUID()), @h, 1, '{\"version\":1}', 'investigador1')");

        assertThatThrownBy(() -> jdbc.update("INSERT INTO hallazgo_version (id, hallazgo_id, version, snapshot, autor)"
                + " VALUES (UUID_TO_BIN(UUID()), @h, 1, '{\"version\":1}', 'otro')"))
                .isInstanceOf(DataAccessException.class);
    }

    /** Inserta un hallazgo y deja su id en la variable de sesion {@code @h}. */
    private void insertarHallazgo() {
        jdbc.update("SET @h = UUID_TO_BIN(UUID())");
        jdbc.update("INSERT INTO hallazgo (id, version, nombre_cientifico, familia, fecha_recoleccion,"
                + " investigador, departamento, estado_conservacion)"
                + " VALUES (@h, 1, 'Atelopus ebenoides', 'Bufonidae', '2026-03-14', 'investigador1',"
                + " 'Boyaca', 'NO_AMENAZADA')");
    }
}
