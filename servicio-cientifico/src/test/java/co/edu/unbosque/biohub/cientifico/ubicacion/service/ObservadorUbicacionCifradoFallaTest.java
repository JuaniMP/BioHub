package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import co.edu.unbosque.biohub.cientifico.eventos.domain.HallazgoRegistrado;
import co.edu.unbosque.biohub.cientifico.ubicacion.BaseUbicacionMySqlTest;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;

/** ARQ-08 y ASR6: "si el cifrado falla, se revierte todo el registro" (SAD §6.4.2). */
class ObservadorUbicacionCifradoFallaTest extends BaseUbicacionMySqlTest {

    @MockitoBean
    private CifradorCoordenadas cifrador;
    @Autowired
    private ApplicationEventPublisher publicador;

    @Test
    @DisplayName("ARQ-08 ASR6: si el cifrado falla durante el evento, no queda ninguna fila y la excepción llega al publicador")
    void cifradoFallidoRevierteElRegistro() {
        given(cifrador.cifrar(any(), any())).willThrow(new IllegalStateException("No se pudo cifrar la ubicación"));
        UUID sinCoordenadas = UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            publicador.publishEvent(new HallazgoRegistrado(sinCoordenadas, 1, "Rhinella marina", LocalDate.of(2026, 3, 14),
                    "inv", "15", "NO_AMENAZADA", null, null, Instant.now())); // este sí se guarda... hasta el fallo
            publicador.publishEvent(new HallazgoRegistrado(UUID.randomUUID(), 1, "Atelopus ebenoides",
                    LocalDate.of(2026, 3, 14), "inv", "15", "EN_PELIGRO", 5.0, -73.0, Instant.now()));
        })).isInstanceOf(IllegalStateException.class).hasMessage("No se pudo cifrar la ubicación");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isZero();
    }
}
