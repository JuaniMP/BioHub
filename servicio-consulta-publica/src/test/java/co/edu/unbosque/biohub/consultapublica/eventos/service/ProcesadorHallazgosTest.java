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

    // ---- SEC-J-01: alertas ante saltos anómalos de version ----

    private java.util.List<ch.qos.logback.classic.spi.ILoggingEvent> ejecutarCapturando(Runnable r) {
        ch.qos.logback.classic.Logger logger = (ch.qos.logback.classic.Logger)
                org.slf4j.LoggerFactory.getLogger(ProcesadorHallazgos.class);
        ch.qos.logback.classic.Level antes = logger.getLevel();
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> logs =
                new ch.qos.logback.core.read.ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        logger.setLevel(ch.qos.logback.classic.Level.DEBUG);
        try {
            r.run();
        } finally {
            logger.detachAppender(logs);
            logger.setLevel(antes);
        }
        return new java.util.ArrayList<>(logs.list);
    }

    private FichaPublica conVersion(int version) {
        return new FichaPublica(hallazgo, version, "Atelopus", null, "Bufonidae", null, LocalDate.of(2026, 3, 14),
                EstadoConservacion.EN_PELIGRO, "15", "Boyacá", "Andina", null);
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-J-01: un salto de version mayor que el umbral se registra como ALERTA (WARN)")
    void alertaAnteUnSaltoAnomaloHaciaArriba() {
        FichaPublica salto = conVersion(900_000);
        when(validador.validar(any(), any())).thenReturn(salto);
        when(proyector.versionGuardada(hallazgo)).thenReturn(java.util.Optional.of(3));
        when(proyector.aplicar(salto)).thenReturn(ResultadoProyeccion.ACTUALIZADA);

        var eventos = ejecutarCapturando(() -> procesador.procesar(evento));

        assertThat(eventos).anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                && e.getFormattedMessage().contains("ALERTA SEC-J-01") && e.getFormattedMessage().contains("900000"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-J-01: un primer evento con version enorme también alerta")
    void alertaSiElPrimerEventoTraeUnaVersionEnorme() {
        FichaPublica nueva = conVersion(500_000);
        when(validador.validar(any(), any())).thenReturn(nueva);
        when(proyector.aplicar(nueva)).thenReturn(ResultadoProyeccion.CREADA);
        var eventos = ejecutarCapturando(() -> procesador.procesar(evento));
        assertThat(eventos).anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                && e.getFormattedMessage().contains("ALERTA SEC-J-01"));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-J-01: un evento legítimo ignorado porque la ficha tiene una version inflada alerta (antes era DEBUG)")
    void alertaSiSeIgnoraUnEventoMuyInferiorALaGuardada() {
        FichaPublica legitima = conVersion(4);
        when(validador.validar(any(), any())).thenReturn(legitima);
        when(proyector.versionGuardada(hallazgo)).thenReturn(java.util.Optional.of(900_000));
        when(proyector.aplicar(legitima)).thenReturn(ResultadoProyeccion.IGNORADA);

        var eventos = ejecutarCapturando(() -> procesador.procesar(evento));

        assertThat(eventos).anyMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN
                && e.getFormattedMessage().contains("congelada"));
        verify(invalidador, never()).invalidar(any());
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-J-01: cambios normales (version +1) y duplicados no generan alertas")
    void sinAlertasEnElUsoNormal() {
        FichaPublica siguiente = conVersion(4);
        when(validador.validar(any(), any())).thenReturn(siguiente);
        when(proyector.versionGuardada(hallazgo)).thenReturn(java.util.Optional.of(3));
        when(proyector.aplicar(siguiente)).thenReturn(ResultadoProyeccion.ACTUALIZADA);
        assertThat(ejecutarCapturando(() -> procesador.procesar(evento)))
                .noneMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN);

        when(proyector.versionGuardada(hallazgo)).thenReturn(java.util.Optional.of(4));
        when(proyector.aplicar(siguiente)).thenReturn(ResultadoProyeccion.IGNORADA);
        assertThat(ejecutarCapturando(() -> procesador.procesar(evento)))
                .noneMatch(e -> e.getLevel() == ch.qos.logback.classic.Level.WARN);

        assertThat(ProcesadorHallazgos.esSaltoAnomalo(3, 3 + ProcesadorHallazgos.SALTO_ANOMALO)).isFalse();
        assertThat(ProcesadorHallazgos.esSaltoAnomalo(3, 4 + ProcesadorHallazgos.SALTO_ANOMALO)).isTrue();
        assertThat(ProcesadorHallazgos.esSaltoAnomalo(Integer.MIN_VALUE, Integer.MAX_VALUE)).isTrue();
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-J-01: si falla la lectura informativa de la version, la proyección sigue su curso")
    void sinLecturaDeVersionNoSeInterrumpeElProcesamiento() {
        when(validador.validar(any(), any())).thenReturn(ficha);
        when(proyector.versionGuardada(hallazgo)).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("bd"));
        when(proyector.aplicar(ficha)).thenReturn(ResultadoProyeccion.ACTUALIZADA);
        assertThat(procesador.procesar(evento)).isEqualTo(ResultadoProyeccion.ACTUALIZADA);
    }
}
