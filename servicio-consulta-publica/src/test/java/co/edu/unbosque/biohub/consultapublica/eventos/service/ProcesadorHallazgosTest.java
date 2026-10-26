package co.edu.unbosque.biohub.consultapublica.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import co.edu.unbosque.biohub.consultapublica.domain.EstadoConservacion;
import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.domain.ResultadoProyeccion;
import co.edu.unbosque.biohub.consultapublica.eventos.domain.EventoSobre;
import co.edu.unbosque.biohub.consultapublica.service.InvalidadorCacheFichas;
import co.edu.unbosque.biohub.consultapublica.service.ProyectorFichas;

class ProcesadorHallazgosTest {

    private final ValidadorDatosHallazgo validador = mock(ValidadorDatosHallazgo.class);
    private final ProyectorFichas proyector = mock(ProyectorFichas.class);
    private final InvalidadorCacheFichas invalidador = mock(InvalidadorCacheFichas.class);
    private final ProcesadorHallazgos procesador = new ProcesadorHallazgos(validador, proyector, invalidador);

    private final UUID hallazgo = UUID.randomUUID();
    private final FichaPublica ficha = new FichaPublica(hallazgo, 2, "Atelopus", null, "Bufonidae", null,
            LocalDate.of(2026, 3, 14), EstadoConservacion.EN_PELIGRO, "15", "Boyacá", "Andina", null);
    private final EventoSobre evento = new EventoSobre(UUID.randomUUID(), "hallazgo.reclasificado",
            "servicio-cientifico", Instant.now(), "auditor1", Map.of("k", "v"));

    @Test
    void invalidaLaCacheDespuesDeProyectarCuandoHuboCambio() {
        when(validador.validar(evento.id(), evento.datos())).thenReturn(ficha);
        when(proyector.aplicar(ficha)).thenReturn(ResultadoProyeccion.ACTUALIZADA);

        assertThat(procesador.procesar(evento)).isEqualTo(ResultadoProyeccion.ACTUALIZADA);

        InOrder orden = inOrder(proyector, invalidador);
        orden.verify(proyector).aplicar(ficha);
        orden.verify(invalidador).invalidar(hallazgo);
    }

    @Test
    void invalidaTambienTrasUnAlta() {
        when(validador.validar(any(), any())).thenReturn(ficha);
        when(proyector.aplicar(ficha)).thenReturn(ResultadoProyeccion.CREADA);
        procesador.procesar(evento);
        verify(invalidador).invalidar(hallazgo);
    }

    @Test
    void siSeIgnoraElEventoNoSeToCaLaCache() {
        when(validador.validar(any(), any())).thenReturn(ficha);
        when(proyector.aplicar(ficha)).thenReturn(ResultadoProyeccion.IGNORADA);
        assertThat(procesador.procesar(evento)).isEqualTo(ResultadoProyeccion.IGNORADA);
        verify(invalidador, never()).invalidar(any());
    }

    @Test
    void siLaProyeccionFallaNoSeInvalidaYElErrorSePropagaParaReintentar() {
        when(validador.validar(any(), any())).thenReturn(ficha);
        when(proyector.aplicar(ficha)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("bd"));
        assertThatThrownBy(() -> procesador.procesar(evento))
                .isInstanceOf(org.springframework.dao.DataAccessException.class);
        verify(invalidador, never()).invalidar(any());
    }

    @Test
    void siElEventoEsInvalidoNoSeProyecta() {
        when(validador.validar(any(), any())).thenThrow(new EventoInvalidoException("x"));
        assertThatThrownBy(() -> procesador.procesar(evento)).isInstanceOf(EventoInvalidoException.class);
        verify(proyector, never()).aplicar(any());
        verify(invalidador, never()).invalidar(any());
    }
}
