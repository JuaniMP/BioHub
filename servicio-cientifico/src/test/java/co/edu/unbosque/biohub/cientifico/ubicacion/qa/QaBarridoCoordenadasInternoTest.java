package co.edu.unbosque.biohub.cientifico.ubicacion.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;

/** QA: barrido de coordenadas en TODAS las respuestas internas por rol (HB-07/08, MP-03, RC-04, RNF9). */
class QaBarridoCoordenadasInternoTest extends QaBase {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PROHIBIDAS = Set.of("lat", "lon", "lng", "latitud", "longitud", "latitude",
            "longitude", "coordinates", "coordenadas", "geometry", "geom", "ubicacionexacta");

    @BeforeEach
    void datos() {
        limpiar();
        for (EstadoConservacion e : EstadoConservacion.values()) {
            sembrar("Atelopus " + e, LocalDate.of(2026, 3, 14), "investigador1", "15", e, CENTINELA_1[0], CENTINELA_1[1]);
            sembrar("Boana " + e, LocalDate.of(2026, 3, 13), "investigador2", "25", e, CENTINELA_2[0], CENTINELA_2[1]);
        }
        sembrar("Sin coords", LocalDate.of(2026, 1, 1), "investigador1", "15", EstadoConservacion.EN_PELIGRO, null, null);
    }

    private static void claves(JsonNode n, List<String> out) {
        if (n.isObject()) {
            n.fields().forEachRemaining(e -> {
                out.add(e.getKey().toLowerCase(Locale.ROOT));
                claves(e.getValue(), out);
            });
        } else if (n.isArray()) {
            n.forEach(x -> claves(x, out));
        }
    }

    private static void sinCoordenadas(String etiqueta, String cuerpo) throws Exception {
        assertThat(cuerpo).as(etiqueta).doesNotContain("4.6097").doesNotContain("-74.0817").doesNotContain("5.1234567")
                .doesNotContain("-73.1234567");
        if (!cuerpo.isBlank()) {
            List<String> k = new ArrayList<>();
            claves(JSON.readTree(cuerpo), k);
            assertThat(k).as(etiqueta + " claves").doesNotContainAnyElementsOf(PROHIBIDAS);
        }
    }

    @Test
    void ningunRolSalvoAltoRangoRecibeCoordenadasEnNingunaRespuesta() throws Exception {
        List<String> urls = List.of("/api/cientifico/ubicacion/hallazgos", "/api/cientifico/ubicacion/hallazgos?size=100&page=0",
                "/api/cientifico/ubicacion/hallazgos?especie=Atelopus", "/api/cientifico/ubicacion/hallazgos?departamento=15",
                "/api/cientifico/ubicacion/hallazgos?investigador=investigador1&desde=2026-01-01&hasta=2026-12-31",
                "/api/cientifico/ubicacion/hallazgos?bbox=1,2,3,4", "/api/cientifico/ubicacion/hallazgos?size=0",
                "/api/cientifico/ubicacion/mapa", "/api/cientifico/ubicacion/mapa?especie=Atelopus",
                "/api/cientifico/ubicacion/mapa?hallazgoId=xyz");
        for (String rol : ROLES_INTERNOS) {
            if (rol.equals("cientifico_alto_rango")) {
                continue;
            }
            for (String url : urls) {
                MockHttpServletResponse r = mvc.perform(get(java.net.URI.create(url))
                        .header("Authorization", bearer("u-" + rol, rol))).andReturn().getResponse();
                sinCoordenadas(rol + " GET " + url, r.getContentAsString());
                if (url.contains("/hallazgos") && r.getStatus() == 200) {
                    JsonNode contenido = JSON.readTree(r.getContentAsString()).get("content");
                    for (JsonNode f : contenido) {
                        String esperado = f.get("nombreCientifico").asText().startsWith("Sin") ? "SIN_COORDENADAS" : "PROTEGIDAS";
                        assertThat(f.get("estadoCoordenadas").asText()).as("%s %s", rol, f.get("nombreCientifico")).isEqualTo(esperado);
                    }
                }
            }
            MockHttpServletResponse p = mvc.perform(post("/api/cientifico/ubicacion/recifrado")
                    .header("Authorization", bearer("u-" + rol, rol))).andReturn().getResponse();
            sinCoordenadas(rol + " POST recifrado", p.getContentAsString());
        }
    }

    @Test
    void elAltoRangoVeLasCoordenadasExactasDeTodasLasEspeciesYSoloEl() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/api/cientifico/ubicacion/hallazgos?size=100")
                .header("Authorization", bearer("juana", "cientifico_alto_rango"))).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        int visibles = 0;
        for (JsonNode f : JSON.readTree(r.getContentAsString()).get("content")) {
            if (f.get("estadoCoordenadas").asText().equals("VISIBLES")) {
                visibles++;
                double lat = f.get("latitud").asDouble();
                double lon = f.get("longitud").asDouble();
                assertThat(new double[]{lat, lon}).isIn(CENTINELA_1, CENTINELA_2);
            }
        }
        assertThat(visibles).as("6 hallazgos con coordenadas (incluye NO_AMENAZADA y SIN_EVALUAR, D15/D21)").isEqualTo(6);
        // y el administrador, aunque recifre, tampoco las recibe
        MockHttpServletResponse rc = mvc.perform(post("/api/cientifico/ubicacion/recifrado")
                .header("Authorization", bearer("admin1", "administrador"))).andReturn().getResponse();
        assertThat(rc.getStatus()).isEqualTo(200);
        sinCoordenadas("recifrado admin", rc.getContentAsString());
    }
}
