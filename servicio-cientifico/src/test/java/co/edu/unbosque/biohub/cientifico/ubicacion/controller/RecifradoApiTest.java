package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import co.edu.unbosque.biohub.cientifico.ubicacion.BaseUbicacionMySqlTest;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.EstadoConservacion;

class RecifradoApiTest extends BaseUbicacionMySqlTest {

    private static final String URL = "/api/cientifico/ubicacion/recifrado";

    @Test
    void sinTokenOVencidoDa401() throws Exception {
        mvc.perform(post(URL)).andExpect(status().isUnauthorized()).andExpect(content().string(""));
        mvc.perform(post(URL).header("Authorization", bearerVencido())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"investigador_campo", "auditor_cientifico", "cientifico_alto_rango", "analista_ambiental"})
    void soloAdministradorPuede(String rol) throws Exception {
        mvc.perform(post(URL).header("Authorization", bearer("u", rol))).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ROL_INSUFICIENTE"));
    }

    @Test
    void administradorRecifraYNuncaVeCoordenadas() throws Exception {
        sembrar("Atelopus a", LocalDate.of(2026, 3, 14), "inv", "15", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.1234567);
        sembrar("Atelopus b", LocalDate.of(2026, 3, 14), "inv", "15", EstadoConservacion.EN_PELIGRO, 6.0, -74.0);
        jdbc.update("UPDATE ubicacion_hallazgo SET clave_version = 'v9' WHERE nombre_cientifico = 'Atelopus b'");
        String cuerpo = mvc.perform(post(URL).header("Authorization", bearer("root", "administrador")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.claveActiva").value("v1"))
                .andExpect(jsonPath("$.revisados").value(1))
                .andExpect(jsonPath("$.recifrados").value(0))
                .andExpect(jsonPath("$.fallidos").value(1))
                .andExpect(jsonPath("$.pendientesPorVersion.v9").value(1))
                .andReturn().getResponse().getContentAsString();
        org.assertj.core.api.Assertions.assertThat(cuerpo).doesNotContain("5.1234567").doesNotContain("latitud");
    }

    @Test
    void sinPendientesDevuelveVacio() throws Exception {
        mvc.perform(post(URL).header("Authorization", bearer("root", "administrador")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.revisados").value(0))
                .andExpect(jsonPath("$.pendientesPorVersion").isEmpty());
    }

    @Test
    void getNoEstaPermitido() throws Exception {
        mvc.perform(get(URL).header("Authorization", bearer("root", "administrador")))
                .andExpect(status().is4xxClientError());
    }
}
