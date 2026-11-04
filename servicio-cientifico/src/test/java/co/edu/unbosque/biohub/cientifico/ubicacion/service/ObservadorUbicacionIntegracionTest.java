package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.IllegalTransactionStateException;

import co.edu.unbosque.biohub.cientifico.eventos.domain.HallazgoReclasificado;
import co.edu.unbosque.biohub.cientifico.eventos.domain.HallazgoRegistrado;
import co.edu.unbosque.biohub.cientifico.ubicacion.BaseUbicacionMySqlTest;

/**
 * ARQ-08 (Observer, SAD §6.4.2): con MySQL real, publicar el evento de dominio guarda la ubicación cifrada en la
 * MISMA transacción del publicador, y un fallo la revierte entera.
 */
class ObservadorUbicacionIntegracionTest extends BaseUbicacionMySqlTest {

    private static final LocalDate FECHA = LocalDate.of(2026, 3, 14);

    @Autowired
    private ApplicationEventPublisher publicador;

    private HallazgoRegistrado registrado(UUID id, String departamento) {
        return new HallazgoRegistrado(id, 1, "Atelopus ebenoides", FECHA, "investigador1", departamento,
                "EN_PELIGRO", 5.1234567, -73.7654321, Instant.now());
    }

    @Test
    @DisplayName("ARQ-08: publicar HallazgoRegistrado en la transacción de registro guarda la ubicación cifrada (RNF9)")
    void registradoGuardaCifrado() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> publicador.publishEvent(registrado(id, "Boyacá")));
        var fila = jdbc.queryForMap("SELECT * FROM ubicacion_hallazgo");
        assertThat(fila.get("departamento_codigo")).isEqualTo("15");
        assertThat((byte[]) fila.get("coord_cifrada")).isNotEmpty();
        assertThat(fila.get("clave_version")).isEqualTo("v1");
        assertThat(fila.values().stream().filter(v -> v != null && !(v instanceof byte[])).map(Object::toString))
                .noneMatch(t -> t.contains("5.1234567") || t.contains("73.7654321"));
    }

    @Test
    @DisplayName("ARQ-08: HallazgoReclasificado actualiza el índice en la misma transacción y gana la versión mayor")
    void reclasificadoActualiza() {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> {
            publicador.publishEvent(registrado(id, "15"));
            publicador.publishEvent(new HallazgoReclasificado(id, 2, "Atelopus muisca", FECHA, "25", "NO_AMENAZADA", Instant.now()));
        });
        var fila = jdbc.queryForMap("SELECT * FROM ubicacion_hallazgo");
        assertThat(fila.get("nombre_cientifico")).isEqualTo("Atelopus muisca");
        assertThat(fila.get("estado_conservacion")).isEqualTo("NO_AMENAZADA");
        assertThat(fila.get("version")).isEqualTo(2);
    }

    @Test
    @DisplayName("ARQ-08: si un evento falla, se revierte todo lo publicado antes en esa transacción (RNF9)")
    void siUnEventoFallaSeRevierteTodo() {
        UUID bueno = UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            publicador.publishEvent(registrado(bueno, "Boyacá"));
            publicador.publishEvent(registrado(UUID.randomUUID(), "Narnia")); // departamento desconocido
        })).isInstanceOf(UbicacionInvalidaException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isZero();
    }

    @Test
    @DisplayName("ARQ-08: sin transacción activa el observador falla (UbicacionService conserva MANDATORY)")
    void sinTransaccionFalla() {
        assertThatThrownBy(() -> publicador.publishEvent(registrado(UUID.randomUUID(), "Boyacá")))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isZero();
    }
}
