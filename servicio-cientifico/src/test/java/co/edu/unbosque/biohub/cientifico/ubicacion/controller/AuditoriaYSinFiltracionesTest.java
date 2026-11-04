package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.biohub.cientifico.ubicacion.BaseUbicacionMySqlTest;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.EstadoConservacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.RegistroUbicacionComando;

/** Auditoría de accesos (D28) y garantía de que ningún log ni respuesta no autorizada contiene coordenadas. */
class AuditoriaYSinFiltracionesTest extends BaseUbicacionMySqlTest {

    private static final String[] NUMEROS = {"5.1234567", "73.7654321", "5,1234567", "5.12345", "73.76543"};
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger raiz;
    private Level nivelRaiz;
    private Level nivelSql;
    private Logger sql;

    @BeforeEach
    void capturarLogs() {
        raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        sql = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        nivelRaiz = raiz.getLevel();
        nivelSql = sql.getLevel();
        raiz.setLevel(Level.DEBUG);
        sql.setLevel(Level.DEBUG);
        logs.start();
        raiz.addAppender(logs);
    }

    @AfterEach
    void restaurar() {
        raiz.detachAppender(logs);
        raiz.setLevel(nivelRaiz);
        sql.setLevel(nivelSql);
    }

    private String todoElLog() {
        List<String> lineas = new ArrayList<>();
        for (ILoggingEvent e : new ArrayList<>(logs.list)) {
            lineas.add(e.getFormattedMessage());
            if (e.getThrowableProxy() != null) {
                lineas.add(e.getThrowableProxy().getMessage());
            }
        }
        return String.join("\n", lineas);
    }

    private List<String> auditoria() {
        return new ArrayList<>(logs.list).stream().filter(e -> e.getLoggerName().equals("biohub.auditoria.ubicacion"))
                .map(ILoggingEvent::getFormattedMessage).collect(Collectors.toList());
    }

    @Test
    void ningunaRespuestaNiLogContieneCoordenadasParaRolesSinPermiso() throws Exception {
        sembrar("Atelopus ebenoides", LocalDate.of(2026, 3, 14), "investigador1", "Boyacá", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.7654321);
        sembrar("Rhinella marina", LocalDate.of(2026, 3, 15), "investigador1", "Boyacá", EstadoConservacion.NO_AMENAZADA, 5.1234567, -73.7654321);
        jdbc.update("UPDATE ubicacion_hallazgo SET coord_cifrada = CONCAT(UNHEX('FF'), SUBSTRING(coord_cifrada, 2)) WHERE nombre_cientifico = 'Rhinella marina'");

        List<String> respuestas = new ArrayList<>();
        for (String rol : List.of("investigador_campo", "auditor_cientifico", "analista_ambiental", "administrador", "sin_rol")) {
            String t = bearer("usuario-" + rol, rol);
            for (String ruta : List.of("/hallazgos", "/hallazgos?especie=Atelopus", "/hallazgos?bbox=1,2,3,4", "/hallazgos?size=0",
                    "/mapa", "/mapa?hallazgoId=xx")) {
                respuestas.add(mvc.perform(get("/api/cientifico/ubicacion" + ruta).header("Authorization", t))
                        .andReturn().getResponse().getContentAsString());
            }
            respuestas.add(mvc.perform(post("/api/cientifico/ubicacion/recifrado").header("Authorization", t))
                    .andReturn().getResponse().getContentAsString());
        }
        for (String cuerpo : respuestas) {
            assertThat(cuerpo).doesNotContain("latitud").doesNotContain("longitud").doesNotContain("coordinates");
            for (String n : NUMEROS) {
                assertThat(cuerpo).doesNotContain(n);
            }
        }
        assertSinNumeros(todoElLog());
    }

    @Test
    void ningunLogContieneCoordenadasNiSiquieraConElAltoRangoNiAlRegistrar() throws Exception {
        sembrar("Atelopus ebenoides", LocalDate.of(2026, 3, 14), "investigador1", "Boyacá", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.7654321);
        // una validación fallida con coordenadas tampoco las filtra
        try {
            tx.executeWithoutResult(s -> ubicacion.registrar(new RegistroUbicacionComando(java.util.UUID.randomUUID(), 1,
                    "Atelopus x", LocalDate.of(2026, 1, 1), "inv", "Narnia", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.7654321)));
        } catch (RuntimeException esperado) {
            assertThat(esperado.getMessage()).doesNotContain("5.1234567");
        }
        String t = bearer("juana", "cientifico_alto_rango");
        mvc.perform(get("/api/cientifico/ubicacion/hallazgos").header("Authorization", t)).andReturn();
        String mapa = mvc.perform(get("/api/cientifico/ubicacion/mapa").header("Authorization", t))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapa).contains("-73.7654321"); // sí las recibe quien tiene permiso
        jdbc.update("UPDATE ubicacion_hallazgo SET coord_cifrada = CONCAT(UNHEX('FF'), SUBSTRING(coord_cifrada, 2))");
        mvc.perform(get("/api/cientifico/ubicacion/mapa").header("Authorization", t)).andReturn(); // error de integridad: se loguea sin datos
        assertSinNumeros(todoElLog());
    }

    @Test
    void laAuditoriaRegistraUsuarioEndpointFiltrosCantidadYDenegaciones() throws Exception {
        sembrar("Atelopus ebenoides", LocalDate.of(2026, 3, 14), "investigador1", "Boyacá", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.7654321);
        mvc.perform(get("/api/cientifico/ubicacion/mapa?especie=Atelopus").header("Authorization", bearer("juana", "cientifico_alto_rango"))).andReturn();
        mvc.perform(get("/api/cientifico/ubicacion/mapa").header("Authorization", bearer("pedro", "auditor_cientifico"))).andReturn();
        mvc.perform(get("/api/cientifico/ubicacion/hallazgos").header("Authorization", bearer("ana", "analista_ambiental"))).andReturn();
        mvc.perform(get("/api/cientifico/ubicacion/hallazgos").header("Authorization", bearer("intruso", "offline_access"))).andReturn();

        List<String> a = auditoria();
        assertThat(a).anyMatch(l -> l.contains("usuario=juana") && l.contains("GET /api/cientifico/ubicacion/mapa")
                && l.contains("resultado=PERMITIDO") && l.contains("especie=Atelopus") && l.contains("cantidad=1") && l.contains("omitidos=0"));
        assertThat(a).anyMatch(l -> l.contains("usuario=pedro") && l.contains("resultado=DENEGADO") && l.contains("/mapa"));
        assertThat(a).anyMatch(l -> l.contains("usuario=ana") && l.contains("/hallazgos") && l.contains("resultado=PERMITIDO")
                && l.contains("coordenadas=false"));
        assertThat(a).anyMatch(l -> l.contains("usuario=intruso") && l.contains("resultado=DENEGADO") && l.contains("/hallazgos"));
        assertSinNumeros(String.join("\n", a));
    }

    @Test
    @org.junit.jupiter.api.DisplayName("SEC-J-08: el 403 de POST /recifrado a un no administrador también se audita (RF13, RNF9)")
    void elIntentoDeRecifradoDeUnNoAdministradorQuedaAuditado() throws Exception {
        mvc.perform(post("/api/cientifico/ubicacion/recifrado").header("Authorization", bearer("intruso", "cientifico_alto_rango")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(post("/api/cientifico/ubicacion/recifrado").header("Authorization", bearer("root", "administrador")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertThat(auditoria()).anyMatch(l -> l.contains("usuario=intruso") && l.contains("POST /api/cientifico/ubicacion/recifrado")
                && l.contains("resultado=DENEGADO"));
        assertThat(auditoria()).as("el re-cifrado permitido sigue auditándose una sola vez")
                .filteredOn(l -> l.contains("usuario=root") && l.contains("resultado=PERMITIDO")).hasSize(1);
        assertThat(auditoria()).filteredOn(l -> l.contains("usuario=root") && l.contains("DENEGADO")).isEmpty();
    }

    @Test
    void laAuditoriaNoPermiteInyectarLineas() throws Exception {
        mvc.perform(get("/api/cientifico/ubicacion/hallazgos").param("especie", "ab\nresultado=PERMITIDO\rfalso")
                .header("Authorization", bearer("ana", "analista_ambiental"))).andReturn();
        assertThat(auditoria()).isNotEmpty().allMatch(l -> !l.contains("\n") && !l.contains("\r"))
                .anyMatch(l -> l.contains("especie=ab_resultado=PERMITIDO_falso"));
    }

    private static void assertSinNumeros(String texto) {
        for (String n : NUMEROS) {
            assertThat(texto).doesNotContain(n);
        }
    }
}
