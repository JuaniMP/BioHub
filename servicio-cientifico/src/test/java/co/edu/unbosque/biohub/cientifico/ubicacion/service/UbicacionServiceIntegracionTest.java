package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.IllegalTransactionStateException;

import co.edu.unbosque.biohub.cientifico.ubicacion.BaseUbicacionMySqlTest;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgoRepository;

class UbicacionServiceIntegracionTest extends BaseUbicacionMySqlTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 14);

    @Autowired
    private CifradorCoordenadas cifrador;
    @Autowired
    private UbicacionHallazgoRepository repositorio;

    private RegistroUbicacionComando alta(UUID id, int version, Double lat, Double lon) {
        return new RegistroUbicacionComando(id, version, "Atelopus ebenoides", FECHA, "investigador1", "Boyacá",
                EstadoConservacion.EN_PELIGRO, lat, lon);
    }

    @Test
    void exigeUnaTransaccionActiva() {
        assertThatThrownBy(() -> ubicacion.registrar(alta(UUID.randomUUID(), 1, 5.0, -73.0)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> ubicacion.actualizar(new ActualizacionUbicacionComando(UUID.randomUUID(), 2,
                "A b", FECHA, "15", EstadoConservacion.SIN_EVALUAR)))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    void registraCifradoYNuncaGuardaCoordenadasEnClaro() {
        UUID id = UUID.randomUUID();
        ResultadoUbicacion resultado = tx.execute(s -> ubicacion.registrar(alta(id, 1, 5.1234567, -73.1234567)));
        assertThat(resultado).isEqualTo(ResultadoUbicacion.APLICADA);

        Map<String, Object> fila = jdbc.queryForMap("SELECT * FROM ubicacion_hallazgo");
        assertThat(fila.get("departamento_codigo")).isEqualTo("15");
        assertThat(fila.get("clave_version")).isEqualTo("v1");
        assertThat((byte[]) fila.get("coord_nonce")).hasSize(12);
        byte[] cifrada = (byte[]) fila.get("coord_cifrada");
        assertThat(cifrada).hasSize(32);
        byte[] plano = ByteBuffer.allocate(16).putDouble(5.1234567).putDouble(-73.1234567).array();
        assertThat(Arrays.equals(Arrays.copyOf(cifrada, 16), plano)).isFalse();
        // ninguna columna de texto contiene los números
        assertThat(fila.values().stream().filter(v -> v != null && !(v instanceof byte[])).map(Object::toString))
                .noneMatch(t -> t.contains("5.1234567") || t.contains("73.1234567"));

        // el descifrado devuelve lo registrado
        Coordenadas c = cifrador.descifrar(id, repositorio.findById(id).orElseThrow().coordenadasCifradas());
        assertThat(c).isEqualTo(new Coordenadas(5.1234567, -73.1234567));
    }

    @Test
    void hallazgoSinCoordenadasDejaNulasLasCuatroColumnas() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> ubicacion.registrar(alta(id, 1, null, null)));
        Map<String, Object> fila = jdbc.queryForMap("SELECT coord_cifrada, coord_nonce, clave_version, cifrado_en FROM ubicacion_hallazgo");
        assertThat(fila.values()).containsOnlyNulls();
    }

    @Test
    void copiarElCifradoAOtraFilaFallaPorElAad() {
        UUID a = sembrar("Atelopus a", FECHA, "i", "15", EstadoConservacion.EN_PELIGRO, 5.0, -73.0);
        UUID b = sembrar("Atelopus b", FECHA, "i", "15", EstadoConservacion.EN_PELIGRO, 6.0, -74.0);
        // El UNIQUE (clave_version, nonce) ya impide copiar el par completo de una fila a otra...
        assertThatThrownBy(() -> jdbc.update("UPDATE ubicacion_hallazgo dst JOIN ubicacion_hallazgo src"
                + " ON src.nombre_cientifico = 'Atelopus a' SET dst.coord_cifrada = src.coord_cifrada,"
                + " dst.coord_nonce = src.coord_nonce WHERE dst.nombre_cientifico = 'Atelopus b'"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        // ... y aunque lo lograra (p. ej. copiando desde un respaldo), el AAD (UUID) hace fallar el tag
        CoordenadasCifradas deA = repositorio.findById(a).orElseThrow().coordenadasCifradas();
        assertThatThrownBy(() -> cifrador.descifrar(b, deA)).isInstanceOf(CoordenadasNoDescifrablesException.class);
        assertThat(cifrador.descifrar(a, repositorio.findById(a).orElseThrow().coordenadasCifradas()))
                .isEqualTo(new Coordenadas(5.0, -73.0));
    }

    @Test
    void alterarLaBdSeDetecta() {
        UUID id = sembrar("Atelopus a", FECHA, "i", "15", EstadoConservacion.EN_PELIGRO, 5.0, -73.0);
        jdbc.update("UPDATE ubicacion_hallazgo SET coord_cifrada = CONCAT(UNHEX('FF'), SUBSTRING(coord_cifrada, 2))");
        CoordenadasCifradas alterada = repositorio.findById(id).orElseThrow().coordenadasCifradas();
        assertThatThrownBy(() -> cifrador.descifrar(id, alterada)).isInstanceOf(CoordenadasNoDescifrablesException.class);
    }

    @Test
    void duplicadoSeIgnoraYNoCambiaNada() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> ubicacion.registrar(alta(id, 1, 5.0, -73.0)));
        byte[] antes = jdbc.queryForObject("SELECT coord_cifrada FROM ubicacion_hallazgo", byte[].class);
        ResultadoUbicacion r = tx.execute(s -> ubicacion.registrar(new RegistroUbicacionComando(id, 9, "Otro nombre",
                FECHA, "otro", "Cauca", EstadoConservacion.SIN_EVALUAR, 1.0, 2.0)));
        assertThat(r).isEqualTo(ResultadoUbicacion.IGNORADA_DUPLICADA);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT coord_cifrada FROM ubicacion_hallazgo", byte[].class)).isEqualTo(antes);
        assertThat(jdbc.queryForObject("SELECT nombre_cientifico FROM ubicacion_hallazgo", String.class)).isEqualTo("Atelopus ebenoides");
    }

    @Test
    void actualizarCambiaSoloLoIndexadoYGanaLaVersionMayor() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> ubicacion.registrar(alta(id, 1, 5.0, -73.0)));
        byte[] cifradoAntes = jdbc.queryForObject("SELECT coord_cifrada FROM ubicacion_hallazgo", byte[].class);

        ResultadoUbicacion aplicada = tx.execute(s -> ubicacion.actualizar(new ActualizacionUbicacionComando(
                id, 2, "Atelopus muisca", FECHA.minusDays(1), "cundinamarca", EstadoConservacion.NO_AMENAZADA)));
        assertThat(aplicada).isEqualTo(ResultadoUbicacion.APLICADA);
        Map<String, Object> f = jdbc.queryForMap("SELECT * FROM ubicacion_hallazgo");
        assertThat(f.get("nombre_cientifico")).isEqualTo("Atelopus muisca");
        assertThat(f.get("departamento_codigo")).isEqualTo("25");
        assertThat(f.get("estado_conservacion")).isEqualTo("NO_AMENAZADA");
        assertThat(f.get("investigador")).isEqualTo("investigador1");
        assertThat(f.get("version")).isEqualTo(2);
        assertThat((byte[]) f.get("coord_cifrada")).isEqualTo(cifradoAntes);

        ResultadoUbicacion vieja = tx.execute(s -> ubicacion.actualizar(new ActualizacionUbicacionComando(
                id, 2, "Atelopus viejo", FECHA, "15", EstadoConservacion.EN_PELIGRO)));
        assertThat(vieja).isEqualTo(ResultadoUbicacion.IGNORADA_VERSION_ANTIGUA);
        assertThat(jdbc.queryForObject("SELECT nombre_cientifico FROM ubicacion_hallazgo", String.class)).isEqualTo("Atelopus muisca");
    }

    @Test
    void actualizarUnHallazgoInexistenteLanzaNoEncontrada() {
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> ubicacion.actualizar(new ActualizacionUbicacionComando(
                UUID.randomUUID(), 2, "A b", FECHA, "15", EstadoConservacion.EN_PELIGRO))))
                .isInstanceOf(UbicacionNoEncontradaException.class);
    }

    @Test
    void unaExcepcionRevierteTodaLaTransaccionDeRegistro() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            ubicacion.registrar(alta(id, 1, 5.0, -73.0));
            ubicacion.registrar(alta(UUID.randomUUID(), 1, 95.0, 0.0)); // inválida: revierte también la primera
        })).isInstanceOf(UbicacionInvalidaException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isZero();
    }

    @Test
    void departamentoPorNombreSinTildesOCodigo() {
        UUID a = sembrar("Atelopus a", FECHA, "i", "boyaca", EstadoConservacion.SIN_EVALUAR, null, null);
        UUID b = sembrar("Atelopus b", FECHA, "i", "11", EstadoConservacion.SIN_EVALUAR, null, null);
        UUID c = sembrar("Atelopus c", FECHA, "i", "BOGOTÁ D.C.", EstadoConservacion.SIN_EVALUAR, null, null);
        List<String> codigos = jdbc.queryForList("SELECT departamento_codigo FROM ubicacion_hallazgo ORDER BY nombre_cientifico", String.class);
        assertThat(codigos).containsExactly("15", "11", "11");
        assertThat(List.of(a, b, c)).doesNotContainNull();
    }
}
