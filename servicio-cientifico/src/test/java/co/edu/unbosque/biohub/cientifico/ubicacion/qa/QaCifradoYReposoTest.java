package co.edu.unbosque.biohub.cientifico.ubicacion.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.containers.Container.ExecResult;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.RegistroUbicacionComando;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.ResultadoUbicacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.UbicacionInvalidaException;

/** QA: CF-03/04/07/08/09/20, DB-01/02/03/05/06/08 y verificación de cifrado en reposo P5 (con controles). */
class QaCifradoYReposoTest extends QaBase {

    @Autowired
    private CifradorCoordenadas cifrador;

    @BeforeEach
    void vaciar() {
        limpiar();
    }

    private RegistroUbicacionComando cmd(UUID id, String nombre, EstadoConservacion e, Double lat, Double lon) {
        return new RegistroUbicacionComando(id, 1, nombre, LocalDate.of(2026, 3, 14), "investigador1", "15", e, lat, lon);
    }

    // ---- CF ------------------------------------------------------------------------------------------------

    @Test
    void cf03_milCifradosDeLaMismaEntradaNuncaRepitenNonceNiTexto() {
        UUID id = UUID.randomUUID();
        Set<String> nonces = new HashSet<>();
        Set<String> textos = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            CoordenadasCifradas c = cifrador.cifrar(id, new Coordenadas(CENTINELA_1[0], CENTINELA_1[1]));
            assertThat(c.nonce()).hasSize(12);
            assertThat(c.textoCifrado()).hasSize(32);
            nonces.add(java.util.HexFormat.of().formatHex(c.nonce()));
            textos.add(java.util.HexFormat.of().formatHex(c.textoCifrado()));
        }
        assertThat(nonces).hasSize(1000);
        assertThat(textos).hasSize(1000);
    }

    @Test
    void cf07_alterarCualquierByteDelTextoOdelNonceSiempreDaIntegridad() {
        UUID id = UUID.randomUUID();
        CoordenadasCifradas ok = cifrador.cifrar(id, new Coordenadas(CENTINELA_2[0], CENTINELA_2[1]));
        assertThat(cifrador.descifrar(id, ok).latitud()).isEqualTo(CENTINELA_2[0]); // control: sin alterar, funciona
        for (int pos = 0; pos < 32; pos++) {
            byte[] t = ok.textoCifrado().clone();
            t[pos] ^= 0x01;
            CoordenadasCifradas mala = new CoordenadasCifradas(t, ok.nonce(), ok.claveVersion());
            assertThatThrownBy(() -> cifrador.descifrar(id, mala)).as("byte %d del texto", pos)
                    .isInstanceOf(CoordenadasNoDescifrablesException.class)
                    .extracting(e -> ((CoordenadasNoDescifrablesException) e).motivo())
                    .isEqualTo(CoordenadasNoDescifrablesException.Motivo.INTEGRIDAD);
        }
        for (int pos = 0; pos < 12; pos++) {
            byte[] n = ok.nonce().clone();
            n[pos] ^= 0x01;
            CoordenadasCifradas mala = new CoordenadasCifradas(ok.textoCifrado(), n, ok.claveVersion());
            assertThatThrownBy(() -> cifrador.descifrar(id, mala)).as("byte %d del nonce", pos)
                    .isInstanceOf(CoordenadasNoDescifrablesException.class);
        }
        // truncado, vacío, nulo, nonce corto
        for (CoordenadasCifradas mala : List.of(
                new CoordenadasCifradas(java.util.Arrays.copyOf(ok.textoCifrado(), 31), ok.nonce(), ok.claveVersion()),
                new CoordenadasCifradas(new byte[0], ok.nonce(), ok.claveVersion()),
                new CoordenadasCifradas(null, ok.nonce(), ok.claveVersion()),
                new CoordenadasCifradas(ok.textoCifrado(), new byte[11], ok.claveVersion()),
                new CoordenadasCifradas(ok.textoCifrado(), null, ok.claveVersion()))) {
            assertThatThrownBy(() -> cifrador.descifrar(id, mala)).isInstanceOf(CoordenadasNoDescifrablesException.class);
        }
    }

    @Test
    void cf09_coordenadasInvalidasSeRechazanSinGuardarNiFiltrarElValor() {
        double[][] malas = {{90.0001, 0}, {-90.0001, 0}, {0, 180.0001}, {0, -200}, {Double.NaN, 0}, {0, Double.NaN},
                {Double.POSITIVE_INFINITY, 0}, {0, Double.NEGATIVE_INFINITY}, {1234.5678, 0}};
        for (double[] m : malas) {
            assertThatThrownBy(() -> tx.executeWithoutResult(s -> ubicacion.registrar(
                    cmd(UUID.randomUUID(), "Atelopus x", EstadoConservacion.EN_PELIGRO, m[0], m[1]))))
                    .as("lat=%s lon=%s", m[0], m[1])
                    .isInstanceOf(UbicacionInvalidaException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain(String.valueOf(m[0]))
                            .doesNotContain(String.valueOf(m[1]).replace("0.0", "ZZZ")));
        }
        // solo una de las dos
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ubicacion.registrar(
                cmd(UUID.randomUUID(), "Atelopus x", EstadoConservacion.EN_PELIGRO, 4.0, null))))
                .isInstanceOf(UbicacionInvalidaException.class);
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ubicacion.registrar(
                cmd(UUID.randomUUID(), "Atelopus x", EstadoConservacion.EN_PELIGRO, null, -74.0))))
                .isInstanceOf(UbicacionInvalidaException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isZero();
    }

    @Test
    void cf09b_bordesValidosHacenIdaYVueltaExacta() {
        double[][] ok = {{90, 180}, {-90, -180}, {0, 0}, {-0.0, 0}, {89.99999999, -179.99999999}};
        for (double[] c : ok) {
            UUID id = UUID.randomUUID();
            tx.executeWithoutResult(s -> assertThat(ubicacion.registrar(
                    cmd(id, "Atelopus borde", EstadoConservacion.EN_PELIGRO, c[0], c[1]))).isEqualTo(ResultadoUbicacion.APLICADA));
            Map<String, Object> f = jdbc.queryForMap("SELECT coord_cifrada, coord_nonce, clave_version FROM ubicacion_hallazgo"
                    + " WHERE hallazgo_id = ?", uuidBytes(id));
            Coordenadas d = cifrador.descifrar(id, new CoordenadasCifradas((byte[]) f.get("coord_cifrada"),
                    (byte[]) f.get("coord_nonce"), (String) f.get("clave_version")));
            assertThat(d.latitud()).isEqualTo(c[0]);
            assertThat(d.longitud()).isEqualTo(c[1]);
        }
    }

    @Test
    void cf04_cf08_mismasCoordenadasEnTresEstadosGuardanSoloCifradoYDistinto() {
        List<UUID> ids = new ArrayList<>();
        for (EstadoConservacion e : EstadoConservacion.values()) {
            ids.add(sembrar("Especie " + e, LocalDate.of(2026, 1, 1), "inv", "15", e, CENTINELA_1[0], CENTINELA_1[1]));
        }
        List<Map<String, Object>> filas = jdbc.queryForList("SELECT coord_cifrada, coord_nonce, clave_version, cifrado_en"
                + " FROM ubicacion_hallazgo");
        assertThat(filas).hasSize(EstadoConservacion.values().length);
        Set<String> cif = filas.stream().map(f -> java.util.HexFormat.of().formatHex((byte[]) f.get("coord_cifrada")))
                .collect(Collectors.toSet());
        Set<String> non = filas.stream().map(f -> java.util.HexFormat.of().formatHex((byte[]) f.get("coord_nonce")))
                .collect(Collectors.toSet());
        assertThat(cif).hasSameSizeAs(filas);
        assertThat(non).hasSameSizeAs(filas);
        filas.forEach(f -> {
            assertThat(f.get("clave_version")).isEqualTo("v1");
            assertThat(f.get("cifrado_en")).isNotNull();
        });
    }

    @Test
    void cf20_cincuentaHilosRegistrandoALaVezNoPierdenNiRepitenNonces() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(50);
        try {
            List<Callable<ResultadoUbicacion>> tareas = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                UUID id = UUID.randomUUID();
                tareas.add(() -> tx.execute(s -> ubicacion.registrar(cmd(id, "Atelopus conc", EstadoConservacion.EN_PELIGRO,
                        CENTINELA_1[0], CENTINELA_1[1]))));
            }
            for (Future<ResultadoUbicacion> f : pool.invokeAll(tareas)) {
                assertThat(f.get()).isEqualTo(ResultadoUbicacion.APLICADA);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT coord_nonce) FROM ubicacion_hallazgo", Integer.class))
                .isEqualTo(50);
    }

    @Test
    void cf20b_elMismoHallazgoRegistradoEnParaleloGuardaUnaSolaFila() throws Exception {
        UUID id = UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(10);
        List<Object> resultados = new ArrayList<>();
        try {
            List<Callable<Object>> tareas = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                tareas.add(() -> {
                    try {
                        return tx.execute(s -> ubicacion.registrar(cmd(id, "Atelopus dup", EstadoConservacion.EN_PELIGRO, 1.0, 1.0)));
                    } catch (RuntimeException e) {
                        return e;
                    }
                });
            }
            for (Future<Object> f : pool.invokeAll(tareas)) {
                resultados.add(f.get());
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isEqualTo(1);
        long aplicadas = resultados.stream().filter(r -> r == ResultadoUbicacion.APLICADA).count();
        assertThat(aplicadas).as("exactamente un alta aplicada; resultados=%s", resultados).isEqualTo(1);
        // Los demás: IGNORADA_DUPLICADA o, en la carrera comprobar-e-insertar, una violación de la PK (la
        // transacción se revierte, nada queda a medias). QA-OBS-01: la carrera lanza excepción en lugar de ignorarse.
        assertThat(resultados.stream().filter(r -> r != ResultadoUbicacion.APLICADA && r != ResultadoUbicacion.IGNORADA_DUPLICADA)
                .allMatch(r -> r instanceof org.springframework.dao.DataIntegrityViolationException))
                .as("solo se admiten IGNORADA_DUPLICADA o violación de PK; resultados=%s", resultados).isTrue();
    }

    @Test
    void us09_textoEspecialSeGuardaLiteralYLaBusquedaLoEncuentra() throws Exception {
        String raro = "Ñandú \"x\" <b>&</b> 'a'; DROP TABLE ubicacion_hallazgo; -- 🐸";
        sembrar(raro, LocalDate.of(2026, 1, 1), "inv", "15", EstadoConservacion.EN_PELIGRO, 1.0, 1.0);
        assertThat(jdbc.queryForObject("SELECT nombre_cientifico FROM ubicacion_hallazgo", String.class)).isEqualTo(raro);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isEqualTo(1);
    }

    // ---- DB / P5 -------------------------------------------------------------------------------------------

    @Test
    void db01_noExisteNingunaColumnaDeCoordenadasEnClaroEnNingunaTabla() {
        List<String> cols = jdbc.queryForList("SELECT CONCAT(table_name,'.',column_name) FROM information_schema.columns"
                + " WHERE table_schema = 'cientifico' AND (LOWER(column_name) REGEXP"
                + " 'lat|lon|lng|coord|geom|point|ubicacion_exacta|x_|y_') AND LOWER(column_name) NOT IN"
                + " ('coord_cifrada','coord_nonce') AND table_name NOT LIKE 'qa\\_%'", String.class);
        assertThat(cols).as("columnas sospechosas de guardar coordenadas en claro").isEmpty();
        List<String> tipos = jdbc.queryForList("SELECT CONCAT(table_name,'.',column_name,':',data_type) FROM information_schema.columns"
                + " WHERE table_schema = 'cientifico' AND table_name = 'ubicacion_hallazgo' AND data_type IN"
                + " ('double','float','decimal','point','geometry')", String.class);
        assertThat(tipos).as("ninguna columna numérica/espacial en ubicacion_hallazgo").isEmpty();
    }

    @Test
    void db08_elCheckDelGrupoCifradoRechazaFilasIncompletas() {
        UUID id = sembrar("Atelopus chk", LocalDate.of(2026, 1, 1), "inv", "15", EstadoConservacion.EN_PELIGRO, 1.0, 1.0);
        assertThatThrownBy(() -> jdbc.update("UPDATE ubicacion_hallazgo SET coord_cifrada = NULL WHERE hallazgo_id = ?",
                (Object) uuidBytes(id))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE ubicacion_hallazgo SET coord_nonce = UNHEX('0011') WHERE hallazgo_id = ?",
                (Object) uuidBytes(id))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE ubicacion_hallazgo SET clave_version = 'v 1!' WHERE hallazgo_id = ?",
                (Object) uuidBytes(id))).isInstanceOf(org.springframework.dao.DataAccessException.class);
        // duplicado (llave, nonce)
        sembrar("Atelopus chk2", LocalDate.of(2026, 1, 1), "inv", "15", EstadoConservacion.EN_PELIGRO, 2.0, 2.0);
        assertThatThrownBy(() -> jdbc.update("UPDATE ubicacion_hallazgo SET coord_nonce = (SELECT n FROM (SELECT coord_nonce n"
                + " FROM ubicacion_hallazgo WHERE hallazgo_id = ?) t) WHERE hallazgo_id <> ?", (Object) uuidBytes(id), (Object) uuidBytes(id)))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    private static byte[] uuidBytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    // ---- P5: cifrado en reposo con control positivo y negativo -----------------------------------------------

    /** Patrones (texto decimal, double big-endian, double little-endian) de un par de coordenadas, en regex PCRE. */
    private static List<String> patrones(double lat, double lon) {
        List<String> p = new ArrayList<>();
        for (double d : new double[]{lat, lon}) {
            p.add(java.util.regex.Pattern.quote(String.valueOf(d)));
            byte[] be = ByteBuffer.allocate(8).putDouble(d).array();
            byte[] le = ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putDouble(d).array();
            p.add(escapar(be));
            p.add(escapar(le));
        }
        return p;
    }

    private static String escapar(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) {
            sb.append(String.format("\\x%02x", x & 0xff));
        }
        return sb.toString();
    }

    private String ejecutar(String... cmd) throws Exception {
        ExecResult r = MYSQL.execInContainer(cmd);
        return r.getStdout();
    }

    /** Cantidad de archivos del datadir de MySQL que contienen alguno de los patrones. */
    private int archivosConPatrones(List<String> patrones) throws Exception {
        String regex = String.join("|", patrones);
        String sal = ejecutar("sh", "-c", "grep -rlaP -e '" + regex.replace("'", "'\\''") + "' /var/lib/mysql 2>/dev/null | wc -l");
        return Integer.parseInt(sal.trim());
    }

    @Test
    void p5_ningunaCoordenadaEnClaroEnBdVolcadoNiArchivosConControlPositivo() throws Exception {
        double[] ctrl = {7.7654321, -71.7654321}; // control positivo: sí se guarda en claro, en una tabla de prueba
        jdbc.execute("CREATE TABLE qa_control_plano (id INT PRIMARY KEY, lat DOUBLE, lon DOUBLE, txt VARCHAR(40), raw VARBINARY(16))");
        jdbc.update("INSERT INTO qa_control_plano VALUES (1, ?, ?, ?, ?)", ctrl[0], ctrl[1], ctrl[0] + "," + ctrl[1],
                ByteBuffer.allocate(16).putDouble(ctrl[0]).putDouble(ctrl[1]).array());

        for (EstadoConservacion e : EstadoConservacion.values()) {
            sembrar("Especie " + e, LocalDate.of(2026, 3, 14), "investigador1", "15", e, CENTINELA_1[0], CENTINELA_1[1]);
            sembrar("Otra " + e, LocalDate.of(2026, 3, 15), "investigador1", "25", e, CENTINELA_2[0], CENTINELA_2[1]);
        }
        // Tráfico de lectura como alto rango (el descifrado no debe persistir nada)
        for (String url : List.of("/api/cientifico/ubicacion/mapa", "/api/cientifico/ubicacion/hallazgos")) {
            String cuerpo = mvc.perform(get(url).header("Authorization", bearer("juana", "cientifico_alto_rango")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(cuerpo).as("el alto rango sí recibe las coordenadas (control de la prueba)").contains("4.6097");
        }
        ejecutar("sh", "-c", "mysql -uroot -ptest -e 'FLUSH TABLES; SET GLOBAL innodb_flush_log_at_trx_commit=1' 2>/dev/null");

        // 1) Volcado lógico (mysqldump) en texto y en hex
        String dump = ejecutar("sh", "-c", "mysqldump -uroot -ptest --hex-blob --skip-comments cientifico 2>/dev/null");
        assertThat(dump).contains("qa_control_plano").contains("ubicacion_hallazgo");
        List<String> txt = List.of("4.6097", "-74.0817", "5.1234567", "-73.1234567");
        for (String t : txt) {
            assertThat(dump).as("dump no debe contener %s", t).doesNotContain(t);
        }
        assertThat(dump).as("control positivo del dump: la tabla en claro SÍ se detecta").contains("7.7654321");
        String dumpHexBuscable = dump.toLowerCase();
        for (double d : new double[]{CENTINELA_1[0], CENTINELA_1[1], CENTINELA_2[0], CENTINELA_2[1]}) {
            assertThat(dumpHexBuscable).doesNotContain(java.util.HexFormat.of().formatHex(ByteBuffer.allocate(8).putDouble(d).array()));
        }
        assertThat(dumpHexBuscable).as("control positivo hex BE")
                .contains(java.util.HexFormat.of().formatHex(ByteBuffer.allocate(8).putDouble(ctrl[0]).array()));
        // Las llaves no están en la BD (DB-05)
        assertThat(dump).doesNotContain(K1).doesNotContain(K2);

        // 2) Valores de todas las columnas de todas las filas, convertidos a texto
        List<String> columnas = jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema='cientifico'"
                + " AND table_name='ubicacion_hallazgo'", String.class);
        for (String c : columnas) {
            List<String> valores = jdbc.queryForList("SELECT CAST(`" + c + "` AS CHAR CHARACTER SET latin1) FROM ubicacion_hallazgo", String.class);
            for (String v : valores) {
                if (v == null) {
                    continue;
                }
                for (String t : txt) {
                    assertThat(v).as("columna %s", c).doesNotContain(t);
                }
            }
        }

        // 3) Archivos del datadir (incluye redo log): control positivo y negativo
        int control = archivosConPatrones(patrones(ctrl[0], ctrl[1]));
        int reales = archivosConPatrones(java.util.stream.Stream.concat(patrones(CENTINELA_1[0], CENTINELA_1[1]).stream(),
                patrones(CENTINELA_2[0], CENTINELA_2[1]).stream()).toList());
        assertThat(control).as("control positivo: las coordenadas en claro de la tabla de control deben detectarse en el datadir").isGreaterThan(0);
        assertThat(reales).as("archivos del datadir de MySQL que contienen las coordenadas centinela").isZero();
        jdbc.execute("DROP TABLE qa_control_plano");
    }

    @org.junit.jupiter.api.AfterEach
    void quitarControl() {
        jdbc.execute("DROP TABLE IF EXISTS qa_control_plano");
    }
}
