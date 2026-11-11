package co.edu.unbosque.biohub.cientifico.registro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import co.edu.unbosque.biohub.cientifico.registro.domain.CategoriaUicn;
import co.edu.unbosque.biohub.cientifico.registro.service.DatosHallazgoInvalidosException;
import co.edu.unbosque.biohub.cientifico.registro.service.HallazgoService;
import co.edu.unbosque.biohub.cientifico.registro.service.ReclasificarHallazgoComando;
import co.edu.unbosque.biohub.cientifico.registro.service.RegistrarHallazgoComando;
import co.edu.unbosque.biohub.cientifico.registro.service.ResultadoRegistro;

/**
 * Prueba la colaboración real entre registro y ubicación (04-contrato-api §6): el hallazgo se inserta,
 * ubicación cifra las coordenadas en la misma transacción y, si algo falla de su lado, no queda nada a
 * medias.
 *
 * <p>Requiere Docker (Testcontainers).
 */
class RegistroConUbicacionTest extends BaseRegistroMySqlTest {

    private static final double LATITUD = 5.53214;
    private static final double LONGITUD = -73.36712;

    @Autowired
    private HallazgoService hallazgos;

    /** Nace como NO_AMENAZADA (LC) para que reclasificar a CR sea un cambio de estado de verdad. */
    private static RegistrarHallazgoComando comando(String departamento, Double lat, Double lon) {
        return new RegistrarHallazgoComando("Atelopus ebenoides", "Rana arlequin", "Bufonidae", "Adulto",
                LocalDate.of(2026, 3, 14), departamento, CategoriaUicn.LC, "Anfibio de paramo", lat, lon, null);
    }

    @Test
    @DisplayName("al registrar, ubicacion guarda las coordenadas cifradas en la misma transaccion")
    void elAltaLlegaAUbicacionCifrada() {
        ResultadoRegistro resultado = hallazgos.registrar(comando("Boyaca", LATITUD, LONGITUD), "investigador1");

        Integer hallazgosGuardados = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hallazgo WHERE id = UUID_TO_BIN(?)", Integer.class,
                resultado.hallazgoId().toString());
        assertThat(hallazgosGuardados).isEqualTo(1);

        Integer ubicacionesGuardadas = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ubicacion_hallazgo WHERE hallazgo_id = UUID_TO_BIN(?)"
                        + " AND coord_cifrada IS NOT NULL", Integer.class, resultado.hallazgoId().toString());
        assertThat(ubicacionesGuardadas).isEqualTo(1);

        Integer versiones = jdbc.queryForObject(
                "SELECT COUNT(*) FROM hallazgo_version WHERE hallazgo_id = UUID_TO_BIN(?) AND version = 1",
                Integer.class, resultado.hallazgoId().toString());
        assertThat(versiones).isEqualTo(1);
    }

    @Test
    @DisplayName("si ubicacion rechaza el departamento, se traduce a un error de registro y no queda nada")
    void siUbicacionFallaSeRevierteTodo() {
        // La excepcion nace en ubicacion y el servicio la traduce, porque D53 impide importarla.
        assertThatThrownBy(() -> hallazgos.registrar(comando("Narnia", LATITUD, LONGITUD), "investigador1"))
                .isInstanceOf(DatosHallazgoInvalidosException.class)
                .extracting(e -> ((DatosHallazgoInvalidosException) e).campo())
                .isEqualTo("departamento");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hallazgo", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hallazgo_version", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isZero();
    }

    @Test
    @DisplayName("un hallazgo sin coordenadas se registra igual; ubicacion solo indexa")
    void hallazgoSinCoordenadas() {
        ResultadoRegistro resultado = hallazgos.registrar(comando("Boyaca", null, null), "investigador1");

        Integer sinCifrar = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ubicacion_hallazgo WHERE hallazgo_id = UUID_TO_BIN(?)"
                        + " AND coord_cifrada IS NULL", Integer.class, resultado.hallazgoId().toString());
        assertThat(sinCifrar).isEqualTo(1);
    }

    @Test
    @DisplayName("reclasificar sube la version en hallazgo, en el historial y en ubicacion")
    void laReclasificacionSincronizaLasTresTablas() {
        UUID id = hallazgos.registrar(comando("Boyaca", LATITUD, LONGITUD), "investigador1").hallazgoId();

        ResultadoRegistro reclasificado = hallazgos.reclasificar(id,
                new ReclasificarHallazgoComando(null, null, null, null, null, null, CategoriaUicn.CR, null, null,
                        "Pruebas de laboratorio confirman que esta en peligro critico"),
                "cientifico1");

        assertThat(reclasificado.version()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT version FROM hallazgo WHERE id = UUID_TO_BIN(?)",
                Integer.class, id.toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT version FROM ubicacion_hallazgo WHERE hallazgo_id = UUID_TO_BIN(?)",
                Integer.class, id.toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM hallazgo_version WHERE hallazgo_id = UUID_TO_BIN(?)",
                Integer.class, id.toString())).isEqualTo(2);
        // Nacio NO_AMENAZADA; tras la reclasificacion ubicacion debe verlo EN_PELIGRO y cifrar en consecuencia.
        assertThat(jdbc.queryForObject("SELECT estado_conservacion FROM ubicacion_hallazgo"
                        + " WHERE hallazgo_id = UUID_TO_BIN(?)", String.class, id.toString()))
                .isEqualTo("EN_PELIGRO");
    }

    @Test
    @DisplayName("la auditoria deja el valor previo y el nuevo con su justificacion (HU-05)")
    void laAuditoriaRegistraElCambio() {
        UUID id = hallazgos.registrar(comando("Boyaca", null, null), "investigador1").hallazgoId();
        hallazgos.reclasificar(id, new ReclasificarHallazgoComando(null, null, null, null, null, null,
                CategoriaUicn.CR, null, null, "Revision taxonomica de laboratorio"), "cientifico1");

        String valorPrevio = jdbc.queryForObject("SELECT valor_previo FROM hallazgo_auditoria"
                        + " WHERE hallazgo_id = UUID_TO_BIN(?) AND campo = 'estadoConservacion'",
                String.class, id.toString());
        String valorNuevo = jdbc.queryForObject("SELECT valor_nuevo FROM hallazgo_auditoria"
                        + " WHERE hallazgo_id = UUID_TO_BIN(?) AND campo = 'estadoConservacion'",
                String.class, id.toString());

        assertThat(valorPrevio).isEqualTo("NO_AMENAZADA");
        assertThat(valorNuevo).isEqualTo("EN_PELIGRO");
    }
}
