package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import co.edu.unbosque.biohub.cientifico.eventos.domain.HallazgoReclasificado;
import co.edu.unbosque.biohub.cientifico.eventos.domain.HallazgoRegistrado;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;

/** ARQ-08 (Observer, P-A3): el observador traduce los eventos de dominio en llamadas a UbicacionService. */
class ObservadorUbicacionTest {

    private final UbicacionService ubicacion = mock(UbicacionService.class);
    private final ObservadorUbicacion observador = new ObservadorUbicacion(ubicacion);
    private final UUID id = UUID.randomUUID();
    private final LocalDate fecha = LocalDate.of(2026, 3, 14);

    @Test
    @DisplayName("ARQ-08: HallazgoRegistrado se traduce en registrar(...) con todos los datos")
    void registrado() {
        when(ubicacion.registrar(any())).thenReturn(ResultadoUbicacion.APLICADA);
        observador.alRegistrarse(new HallazgoRegistrado(id, 1, "Atelopus ebenoides", fecha, "inv", "Boyacá",
                "EN_PELIGRO", 5.5, -73.5, Instant.now()));
        ArgumentCaptor<RegistroUbicacionComando> c = ArgumentCaptor.forClass(RegistroUbicacionComando.class);
        verify(ubicacion).registrar(c.capture());
        assertThat(c.getValue()).isEqualTo(new RegistroUbicacionComando(id, 1, "Atelopus ebenoides", fecha, "inv",
                "Boyacá", EstadoConservacion.EN_PELIGRO, 5.5, -73.5));
    }

    @Test
    @DisplayName("ARQ-08: HallazgoReclasificado se traduce en actualizar(...)")
    void reclasificado() {
        observador.alReclasificarse(new HallazgoReclasificado(id, 2, "Atelopus muisca", fecha, "25",
                "NO_AMENAZADA", Instant.now()));
        verify(ubicacion).actualizar(new ActualizacionUbicacionComando(id, 2, "Atelopus muisca", fecha, "25",
                EstadoConservacion.NO_AMENAZADA));
    }

    @Test
    @DisplayName("ARQ-08: un estado desconocido es UbicacionInvalidaException; un estado nulo lo rechaza el servicio (obligatorio)")
    void estadoInvalido() {
        assertThatThrownBy(() -> observador.alRegistrarse(new HallazgoRegistrado(id, 1, "A b", fecha, "inv", "15",
                "INVENTADO", null, null, Instant.now())))
                .isInstanceOfSatisfying(UbicacionInvalidaException.class, e -> assertThat(e.campo()).isEqualTo("estadoConservacion"));
        verifyNoInteractions(ubicacion);
        observador.alRegistrarse(new HallazgoRegistrado(id, 1, "A b", fecha, "inv", "15", null, null, null, Instant.now()));
        verify(ubicacion).registrar(any());
    }

    @Test
    @DisplayName("ARQ-08: las excepciones del servicio suben al publicador (así se revierte el registro)")
    void laExcepcionSePropaga() {
        when(ubicacion.registrar(any())).thenThrow(new IllegalStateException("No se pudo cifrar la ubicación"));
        assertThatThrownBy(() -> observador.alRegistrarse(new HallazgoRegistrado(id, 1, "A b", fecha, "inv", "15",
                "SIN_EVALUAR", 1.0, 2.0, Instant.now())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ARQ-08 RNF9: el evento de dominio y el comando no imprimen coordenadas")
    void elEventoNoImprimeCoordenadas() {
        HallazgoRegistrado e = new HallazgoRegistrado(id, 1, "A b", fecha, "inv", "15", "SIN_EVALUAR",
                5.1234567, -73.7654321, Instant.now());
        assertThat(e.toString()).doesNotContain("5.1234567").doesNotContain("73.7654321").contains("coordenadas=***");
    }
}
