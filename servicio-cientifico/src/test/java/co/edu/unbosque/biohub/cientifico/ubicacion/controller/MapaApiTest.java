package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import co.edu.unbosque.biohub.cientifico.ubicacion.BaseUbicacionMySqlTest;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.RegistroUbicacionComando;

class MapaApiTest extends BaseUbicacionMySqlTest {

    private static final String URL = "/api/cientifico/ubicacion/mapa";

    private ResultActions mapa(String auth, String query) throws Exception {
        return mvc.perform(get(java.net.URI.create(URL + query.replace(" ", "%20"))).header("Authorization", auth));
    }

    private UUID dosHallazgos() {
        UUID a = sembrar("Atelopus ebenoides", LocalDate.of(2026, 3, 14), "investigador1", "Boyacá", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.1234567);
        sembrar("Rhinella marina", LocalDate.of(2025, 12, 1), "investigador1", "Boyacá", EstadoConservacion.NO_AMENAZADA, null, null);
        sembrar("Atelopus muisca", LocalDate.of(2026, 2, 2), "investigador2", "Cundinamarca", EstadoConservacion.NO_AMENAZADA, 4.711, -74.0721);
        return a;
    }

    @Test
    void sinTokenOVencidoDa401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mapa(bearerVencido(), "").andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"investigador_campo", "auditor_cientifico", "analista_ambiental", "administrador", "offline_access"})
    void losDemasRolesRecibenElMapaDenegado(String rol) throws Exception {
        dosHallazgos();
        mapa(bearer("u", rol), "").andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.codigo").value("ROL_INSUFICIENTE"))
                .andExpect(jsonPath("$.features").doesNotExist());
    }

    @Test
    void altoRangoRecibeGeoJsonConLongitudPrimero() throws Exception {
        UUID a = dosHallazgos();
        mapa(bearer("juana", "cientifico_alto_rango"), "")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.total").value(2)) // el que no tiene coordenadas no cuenta
                .andExpect(jsonPath("$.devueltos").value(2))
                .andExpect(jsonPath("$.truncado").value(false))
                .andExpect(jsonPath("$.omitidos").value(0))
                .andExpect(jsonPath("$.limite").value(1000))
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].id").value(a.toString()))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Point"))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0]").value(-73.1234567))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[1]").value(5.1234567))
                .andExpect(jsonPath("$.features[0].properties.hallazgoId").value(a.toString()))
                .andExpect(jsonPath("$.features[0].properties.nombreCientifico").value("Atelopus ebenoides"))
                .andExpect(jsonPath("$.features[0].properties.departamentoCodigo").value("15"))
                .andExpect(jsonPath("$.features[0].properties.region").value("Andina"))
                .andExpect(jsonPath("$.features[0].properties.estadoConservacion").value("EN_PELIGRO"))
                .andExpect(jsonPath("$.features[1].properties.nombreCientifico").value("Atelopus muisca")); // fecha descendente
    }

    @Test
    void filtros() throws Exception {
        UUID a = dosHallazgos();
        String t = bearer("juana", "cientifico_alto_rango");
        mapa(t, "?especie=atelopus%20e").andExpect(jsonPath("$.total").value(1));
        mapa(t, "?departamento=25").andExpect(jsonPath("$.total").value(1));
        mapa(t, "?departamento=Boyacá&desde=2026-01-01&hasta=2026-12-31").andExpect(jsonPath("$.total").value(1));
        mapa(t, "?desde=2026-03-01").andExpect(jsonPath("$.total").value(1));
        mapa(t, "?especie=Rhinella").andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.features.length()").value(0));
        // hallazgoId ignora los demás filtros
        mapa(t, "?hallazgoId=" + a + "&especie=Nada&departamento=25").andExpect(status().isOk())
                .andExpect(jsonPath("$.features.length()").value(1)).andExpect(jsonPath("$.features[0].id").value(a.toString()));
        mapa(t, "?hallazgoId=" + UUID.randomUUID()).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.devueltos").value(0));
    }

    @Test
    void validaciones() throws Exception {
        String t = bearer("juana", "cientifico_alto_rango");
        mapa(t, "?hallazgoId=xyz").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("hallazgoId"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("identificador inválido"));
        mapa(t, "?desde=2026-05-01&hasta=2026-01-01").andExpect(status().isBadRequest());
        mapa(t, "?especie=a").andExpect(status().isBadRequest());
        mapa(t, "?departamento=Narnia").andExpect(status().isBadRequest());
    }

    @Test
    void truncaEn1000Features() throws Exception {
        // 1.003 hallazgos con coordenadas en una sola transacción
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 1003; i++) {
                ubicacion.registrar(new RegistroUbicacionComando(UUID.randomUUID(), 1, "Atelopus sp" + i,
                        LocalDate.of(2026, 1, 1).plusDays(i % 200), "inv", "15", EstadoConservacion.EN_PELIGRO,
                        1.0 + i * 0.001, -70.0));
            }
        });
        String t = bearer("juana", "cientifico_alto_rango");
        mapa(t, "").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1003)).andExpect(jsonPath("$.devueltos").value(1000))
                .andExpect(jsonPath("$.features.length()").value(1000)).andExpect(jsonPath("$.truncado").value(true));
    }

    @Test
    void registrosIlegiblesSeOmitenYSeCuentan() throws Exception {
        dosHallazgos();
        jdbc.update("UPDATE ubicacion_hallazgo SET clave_version = 'v9' WHERE nombre_cientifico = 'Atelopus muisca'");
        mapa(bearer("juana", "cientifico_alto_rango"), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2)).andExpect(jsonPath("$.devueltos").value(1))
                .andExpect(jsonPath("$.omitidos").value(1)).andExpect(jsonPath("$.features.length()").value(1));
    }
}
