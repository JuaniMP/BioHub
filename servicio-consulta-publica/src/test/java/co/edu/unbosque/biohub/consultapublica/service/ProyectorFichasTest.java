package co.edu.unbosque.biohub.consultapublica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import co.edu.unbosque.biohub.consultapublica.domain.EstadoConservacion;
import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.domain.ResultadoProyeccion;
import co.edu.unbosque.biohub.consultapublica.infrastructure.EscrituraFichasJdbc;

class ProyectorFichasTest {

    private final EscrituraFichasJdbc escritura = mock(EscrituraFichasJdbc.class);
    private final ProyectorFichas proyector = new ProyectorFichas(escritura);
    private final FichaPublica ficha = new FichaPublica(UUID.randomUUID(), 2, "Atelopus", null, "Bufonidae", null,
            LocalDate.of(2026, 3, 14), EstadoConservacion.EN_PELIGRO, "15", "Boyacá", "Andina", null);

    @Test
    void siExisteConVersionMenorSeActualiza() {
        when(escritura.actualizarSiEsMasNueva(ficha)).thenReturn(1);
        assertThat(proyector.aplicar(ficha)).isEqualTo(ResultadoProyeccion.ACTUALIZADA);
        verify(escritura, never()).insertar(any());
    }

    @Test
    void siNoExisteSeInserta() {
        when(escritura.actualizarSiEsMasNueva(ficha)).thenReturn(0);
        assertThat(proyector.aplicar(ficha)).isEqualTo(ResultadoProyeccion.CREADA);
        verify(escritura).insertar(ficha);
    }

    @Test
    void siYaExisteConVersionIgualOMayorSeIgnora() {
        when(escritura.actualizarSiEsMasNueva(ficha)).thenReturn(0, 0);
        doThrow(new DuplicateKeyException("dup")).when(escritura).insertar(ficha);
        assertThat(proyector.aplicar(ficha)).isEqualTo(ResultadoProyeccion.IGNORADA);
        verify(escritura, times(2)).actualizarSiEsMasNueva(ficha);
    }

    @Test
    void siOtroConsumidorInsertoEntreMediasSeReintentaLaActualizacion() {
        when(escritura.actualizarSiEsMasNueva(ficha)).thenReturn(0, 1);
        doThrow(new DuplicateKeyException("dup")).when(escritura).insertar(ficha);
        assertThat(proyector.aplicar(ficha)).isEqualTo(ResultadoProyeccion.ACTUALIZADA);
    }

    @Test
    void sinDuplicadoNoSeReintenta() {
        when(escritura.actualizarSiEsMasNueva(ficha)).thenReturn(0);
        doNothing().when(escritura).insertar(ficha);
        proyector.aplicar(ficha);
        verify(escritura, times(1)).actualizarSiEsMasNueva(ficha);
    }
}
