package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.biohub.cientifico.ubicacion.BaseUbicacionMySqlTest;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;

/** ARQ-10 (RF13, D28): la cadena real de decoradores audita cada acceso a coordenadas exactas, y solo esos. */
class DecoradorAuditoriaIntegracionTest extends BaseUbicacionMySqlTest {

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void capturar() {
        logger = (Logger) LoggerFactory.getLogger("biohub.auditoria.ubicacion");
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void soltar() {
        logger.detachAppender(logs);
    }

    private List<String> auditoria() {
        return new ArrayList<>(logs.list).stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("ARQ-10 RF13: el mapa del alto rango deja una línea de auditoría por cada hallazgo descifrado, sin coordenadas")
    void mapaAudituCadaAccesoExacto() throws Exception {
        UUID a = sembrar("Atelopus a", LocalDate.of(2026, 3, 14), "i", "Boyacá", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.7654321);
        UUID b = sembrar("Atelopus b", LocalDate.of(2026, 3, 15), "i", "Boyacá", EstadoConservacion.NO_AMENAZADA, 6.5, -74.5);
        mvc.perform(get("/api/cientifico/ubicacion/mapa").header("Authorization", bearer("juana", "cientifico_alto_rango"))).andReturn();
        assertThat(auditoria()).filteredOn(l -> l.contains("endpoint=obtenerUbicacionExacta"))
                .hasSize(2)
                .allMatch(l -> l.contains("usuario=juana") && l.contains("resultado=PERMITIDO"))
                .anyMatch(l -> l.contains("hallazgoId=" + a)).anyMatch(l -> l.contains("hallazgoId=" + b))
                .noneMatch(l -> l.contains("5.1234567") || l.contains("73.7654321"));
    }

    @Test
    @DisplayName("ARQ-09 ARQ-10 RF13: la búsqueda de un rol sin permiso no descifra nada, así que no deja auditoría de acceso exacto")
    void rolSinPermisoNoDescifraNiAudituAccesoExacto() throws Exception {
        sembrar("Atelopus a", LocalDate.of(2026, 3, 14), "i", "Boyacá", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.7654321);
        mvc.perform(get("/api/cientifico/ubicacion/hallazgos").header("Authorization", bearer("ana", "analista_ambiental"))).andReturn();
        assertThat(auditoria()).noneMatch(l -> l.contains("endpoint=obtenerUbicacionExacta"));
        assertThat(auditoria()).anyMatch(l -> l.contains("usuario=ana") && l.contains("coordenadas=false"));
    }
}
