package co.edu.unbosque.biohub.consultapublica.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.consultapublica.config.SecurityConfig;
import co.edu.unbosque.biohub.consultapublica.domain.Departamento;
import co.edu.unbosque.biohub.consultapublica.service.CatalogoDepartamentos;
import co.edu.unbosque.biohub.consultapublica.service.ConsultaFichasService;
import co.edu.unbosque.biohub.consultapublica.service.ElectorEstrategiaBusqueda;
import co.edu.unbosque.biohub.consultapublica.service.FichaNoEncontradaException;
import co.edu.unbosque.biohub.consultapublica.service.MonitorCarga;
import co.edu.unbosque.biohub.consultapublica.service.vista.FichaPublicaResponse;
import co.edu.unbosque.biohub.consultapublica.service.vista.PaginaFichasResponse;
import co.edu.unbosque.biohub.consultapublica.service.ValidadorBusqueda;

/** Capa web: validación, códigos de estado, formato de error, cabeceras y seguridad. Sin BD ni Redis. */
@WebMvcTest(FichasPublicasController.class)
@Import({SecurityConfig.class, ValidadorBusqueda.class, ElectorEstrategiaBusqueda.class})
class FichasPublicasControllerTest {

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private ConsultaFichasService servicio;
    @MockitoBean
    private CatalogoDepartamentos catalogo;
    @MockitoBean
    private MonitorCarga monitor; // sin carga: la estrategia es la búsqueda completa

    @Test
    void listadoEsPublicoSinTokenYLlevaCacheControl30s() throws Exception {
        given(servicio.buscar(any())).willReturn(new PaginaFichasResponse(List.of(), 0, 20, 0, 0, false, List.of()));
        mvc.perform(get("/api/publica/fichas"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=30"))
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalPaginas").value(0))
                .andExpect(jsonPath("$.filtrosIgnorados").isEmpty());
    }

    @Test
    void fichaLlevaCacheControl60sYUbicacionProtegida() throws Exception {
        UUID id = UUID.randomUUID();
        given(servicio.obtener(id)).willReturn(FichaPublicaResponse.protegida(id, "Atelopus", null, "Bufonidae", null,
                java.time.LocalDate.of(2026, 3, 14), "EN_PELIGRO", "Boyacá", "15", "Andina", null));
        mvc.perform(get("/api/publica/fichas/" + id.toString().toUpperCase()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60"))
                .andExpect(jsonPath("$.ubicacionProtegida").value(true))
                .andExpect(jsonPath("$.nombreComun").value((Object) null))
                .andExpect(jsonPath("$.fechaRecoleccion").value("2026-03-14"));
    }

    @Test
    void parametrosInvalidosDevuelvenProblemJson400() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "a"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("q"));
    }

    @Test
    void departamentoDesconocidoDevuelve400() throws Exception {
        given(catalogo.resolver("Atlantis")).willReturn(Optional.empty());
        mvc.perform(get("/api/publica/fichas").param("departamento", "Atlantis"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("departamento"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("departamento desconocido"));
    }

    @Test
    void departamentoValidoSeResuelveAlCodigo() throws Exception {
        given(catalogo.resolver("Boyaca")).willReturn(Optional.of(new Departamento("15", "Boyacá", "Andina")));
        given(servicio.buscar(any())).willReturn(new PaginaFichasResponse(List.of(), 0, 20, 0, 0, false, List.of()));
        mvc.perform(get("/api/publica/fichas").param("departamento", "Boyaca")).andExpect(status().isOk());
    }

    @Test
    void fichaNoEncontradaDevuelve404() throws Exception {
        UUID id = UUID.randomUUID();
        given(servicio.obtener(id)).willThrow(new FichaNoEncontradaException());
        mvc.perform(get("/api/publica/fichas/" + id))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.instance").value("/api/publica/fichas/" + id));
    }

    @Test
    void idInvalidoDevuelve400SinLlamarAlServicio() throws Exception {
        mvc.perform(get("/api/publica/fichas/xyz"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("id"));
        mvc.perform(get("/api/publica/fichas/00000000-0000-0000-0000-00000000000g"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void baseDeDatosCaidaDevuelve503SinDetallesInternos() throws Exception {
        given(servicio.buscar(any())).willThrow(new DataAccessResourceFailureException("jdbc:mysql://secreto:3306 caído"));
        mvc.perform(get("/api/publica/fichas"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.detail").value("El servicio no está disponible en este momento. Intenta de nuevo."))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secreto"))));
    }

    @Test
    void errorInesperadoDevuelve500SinTrazas() throws Exception {
        UUID id = UUID.randomUUID();
        given(servicio.obtener(id)).willThrow(new IllegalStateException("detalle interno SELECT * FROM x"));
        mvc.perform(get("/api/publica/fichas/" + id))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("SELECT"))));
    }

    @Test
    void metodoNoPermitidoNoSeConvierteEn500() throws Exception {
        mvc.perform(post("/api/publica/fichas").with(org.springframework.security.test.web.servlet.request
                        .SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void conAuthorizationInvalidoResponde401SinCuerpo() throws Exception {
        given(jwtDecoder.decode("malo")).willThrow(new BadJwtException("inválido"));
        mvc.perform(get("/api/publica/fichas").header("Authorization", "Bearer malo"))
                .andExpect(status().isUnauthorized());
    }
}
