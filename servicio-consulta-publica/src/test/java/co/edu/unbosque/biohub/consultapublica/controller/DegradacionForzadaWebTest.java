package co.edu.unbosque.biohub.consultapublica.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.consultapublica.config.ConfiguracionDegradacion;
import co.edu.unbosque.biohub.consultapublica.config.SecurityConfig;
import co.edu.unbosque.biohub.consultapublica.service.CatalogoDepartamentos;
import co.edu.unbosque.biohub.consultapublica.service.ConsultaFichasService;
import co.edu.unbosque.biohub.consultapublica.service.CriterioBusquedaPublica;
import co.edu.unbosque.biohub.consultapublica.service.ElectorEstrategiaBusqueda;
import co.edu.unbosque.biohub.consultapublica.service.MonitorCarga;
import co.edu.unbosque.biohub.consultapublica.service.ValidadorBusqueda;
import co.edu.unbosque.biohub.consultapublica.service.vista.PaginaFichasResponse;

/** ARQ-06 / RNF1: el booleano CONSULTA_MODO_DEGRADADO sigue siendo el forzado manual, aunque no haya carga. */
@WebMvcTest(FichasPublicasController.class)
@Import({SecurityConfig.class, ValidadorBusqueda.class, ElectorEstrategiaBusqueda.class, MonitorCarga.class,
        ConfiguracionDegradacion.class})
@TestPropertySource(properties = "biohub.publica.modo-degradado=true")
class DegradacionForzadaWebTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MonitorCarga monitor;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private ConsultaFichasService servicio;
    @MockitoBean
    private CatalogoDepartamentos catalogo;

    @Test
    @DisplayName("ARQ-06 RNF1: con modo-degradado=true toda búsqueda es degradada, sin 5xx, y el monitor lo reporta como forzado")
    void forzadoDegradaSiempre() throws Exception {
        given(servicio.buscar(any())).willAnswer(i -> {
            CriterioBusquedaPublica c = i.getArgument(0);
            return new PaginaFichasResponse(List.of(), c.page(), c.size(), 0, 0, c.modoDegradado(), c.filtrosIgnorados());
        });
        mvc.perform(get("/api/publica/fichas?q=rana&familia=Bufonidae"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoDegradado").value(true))
                .andExpect(jsonPath("$.filtrosIgnorados[0]").value("familia"));
        assertThat(monitor.estado().origen()).isEqualTo(MonitorCarga.Origen.FORZADO);
        assertThat(monitor.estado().degradado()).isTrue();
    }
}
