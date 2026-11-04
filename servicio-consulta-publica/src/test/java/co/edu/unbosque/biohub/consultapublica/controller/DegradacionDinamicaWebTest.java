package co.edu.unbosque.biohub.consultapublica.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.consultapublica.config.ConfiguracionDegradacion;
import co.edu.unbosque.biohub.consultapublica.config.SecurityConfig;
import co.edu.unbosque.biohub.consultapublica.domain.Departamento;
import co.edu.unbosque.biohub.consultapublica.service.CatalogoDepartamentos;
import co.edu.unbosque.biohub.consultapublica.service.ConsultaFichasService;
import co.edu.unbosque.biohub.consultapublica.service.ElectorEstrategiaBusqueda;
import co.edu.unbosque.biohub.consultapublica.service.MonitorCarga;
import co.edu.unbosque.biohub.consultapublica.service.ValidadorBusqueda;
import co.edu.unbosque.biohub.consultapublica.service.vista.PaginaFichasResponse;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * ARQ-06 (ASR1, RNF1, HU-06.4): cadena real filtro -> monitor -> elector -> validador -> controlador, sin BD.
 * Entrada con 3 peticiones en curso, salida con 1 o menos, sin permanencia mínima (para no esperar en la prueba).
 */
@WebMvcTest(FichasPublicasController.class)
@Import({SecurityConfig.class, ValidadorBusqueda.class, ElectorEstrategiaBusqueda.class, MonitorCarga.class,
        ConfiguracionDegradacion.class})
@TestPropertySource(properties = {
        "biohub.publica.degradacion.en-curso-entrada=3", "biohub.publica.degradacion.en-curso-salida=1",
        "biohub.publica.degradacion.permanencia-minima=0s", "biohub.publica.degradacion.intervalo-evaluacion=0s"})
class DegradacionDinamicaWebTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MonitorCarga monitor;
    @Autowired
    private InfoContributor busquedaInfoContributor;
    @Autowired
    private MeterBinder monitorCargaMetricas;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private ConsultaFichasService servicio;
    @MockitoBean
    private CatalogoDepartamentos catalogo;

    private void respuestaDelServicioEco() {
        given(servicio.buscar(any())).willAnswer(i -> {
            var c = (co.edu.unbosque.biohub.consultapublica.service.CriterioBusquedaPublica) i.getArgument(0);
            return new PaginaFichasResponse(List.of(), c.page(), c.size(), 0, 0, c.modoDegradado(), c.filtrosIgnorados());
        });
        given(catalogo.resolver("15")).willReturn(java.util.Optional.of(new Departamento("15", "Boyacá", "Andina")));
    }

    @Test
    @DisplayName("ARQ-06 ASR1: con la carga alta la respuesta indica modoDegradado y los filtros ignorados, y al normalizarse vuelve a la completa")
    void degradaConLaCargaYVuelve() throws Exception {
        respuestaDelServicioEco();
        mvc.perform(get("/api/publica/fichas?q=rana&familia=Bufonidae&departamento=15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoDegradado").value(false))
                .andExpect(jsonPath("$.filtrosIgnorados").isEmpty());

        for (int i = 0; i < 3; i++) {
            monitor.iniciarPeticion(); // tres lecturas en curso: umbral de entrada
        }
        mvc.perform(get("/api/publica/fichas?q=rana&familia=Bufonidae&departamento=15"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=30"))
                .andExpect(jsonPath("$.modoDegradado").value(true))
                .andExpect(jsonPath("$.filtrosIgnorados[0]").value("familia"))
                .andExpect(jsonPath("$.filtrosIgnorados[1]").value("departamento"));
        Map<String, Object> busqueda = busqueda();
        assertThat(busqueda).containsEntry("modo", "degradada").containsEntry("origen", "automatico");

        for (int i = 0; i < 3; i++) {
            monitor.terminarPeticion(5);
        }
        mvc.perform(get("/api/publica/fichas?q=rana&familia=Bufonidae&departamento=15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modoDegradado").value(false))
                .andExpect(jsonPath("$.filtrosIgnorados").isEmpty());
        assertThat(busqueda()).containsEntry("modo", "completa").containsEntry("origen", "ninguno");
    }

    @Test
    @DisplayName("ARQ-06: el filtro cierra cada medición: tras las peticiones no quedan lecturas 'en curso'")
    void elFiltroNoDejaPeticionesEnCurso() throws Exception {
        respuestaDelServicioEco();
        for (int i = 0; i < 5; i++) {
            mvc.perform(get("/api/publica/fichas?q=rana")).andExpect(status().isOk());
        }
        assertThat(monitor.estado().enCurso()).isZero();
        assertThat(monitor.estado().muestras()).isEqualTo(5);
    }

    @Test
    @DisplayName("ARQ-06: el estado se expone como métricas (degradado, en curso y p95)")
    void metricas() {
        MeterRegistry registro = new SimpleMeterRegistry();
        monitorCargaMetricas.bindTo(registro);
        assertThat(registro.get("biohub.consulta.degradado").gauge().value()).isZero();
        assertThat(registro.get("biohub.consulta.en_curso").gauge().value()).isZero();
        assertThat(registro.find("biohub.consulta.p95.ms").gauge()).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> busqueda() {
        Info.Builder b = new Info.Builder();
        busquedaInfoContributor.contribute(b);
        return (Map<String, Object>) b.build().get("busqueda");
    }
}
