package co.edu.unbosque.biohub.consultapublica;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import co.edu.unbosque.biohub.consultapublica.config.SecurityConfig;
import co.edu.unbosque.biohub.consultapublica.controller.FichasPublicasController;
import co.edu.unbosque.biohub.consultapublica.controller.PingController;
import co.edu.unbosque.biohub.consultapublica.eventos.controller.EventosRecibidosController;
import co.edu.unbosque.biohub.consultapublica.eventos.service.AlmacenEventosRecibidos;
import co.edu.unbosque.biohub.consultapublica.service.CatalogoDepartamentos;
import co.edu.unbosque.biohub.consultapublica.service.ConsultaFichasService;
import co.edu.unbosque.biohub.consultapublica.service.ElectorEstrategiaBusqueda;
import co.edu.unbosque.biohub.consultapublica.service.MonitorCarga;
import co.edu.unbosque.biohub.consultapublica.service.ValidadorBusqueda;

/**
 * Seguridad de servicio-consulta-publica: los tres GET públicos responden sin token, los eventos recibidos
 * son solo de administrador y todo lo demás se deniega (denyAll). Capa web con servicios simulados.
 */
@WebMvcTest({FichasPublicasController.class, PingController.class, EventosRecibidosController.class})
@Import({SecurityConfig.class, ValidadorBusqueda.class, ElectorEstrategiaBusqueda.class})
class SeguridadEndpointsTest {

    private static final List<String> ROLES = List.of("investigador_campo", "auditor_cientifico",
            "cientifico_alto_rango", "analista_ambiental", "administrador");
    private static final String FICHA = "/api/publica/fichas/" + UUID.randomUUID();
    private static final String EVENTOS = "/api/publica/eventos/recibidos";

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private ConsultaFichasService servicio;
    @MockitoBean
    private CatalogoDepartamentos catalogo;
    @MockitoBean
    private MonitorCarga monitor;
    @MockitoBean
    private AlmacenEventosRecibidos almacen;

    private static RequestPostProcessor conRoles(String... roles) {
        return jwt().jwt(j -> j.claim("preferred_username", "usuario1"))
                .authorities(Stream.of(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    static Stream<String> publicos() {
        return Stream.of("/api/publica/ping", "/api/publica/fichas", FICHA);
    }

    // ---- públicos ----

    @ParameterizedTest(name = "GET {0} responde sin token")
    @MethodSource("publicos")
    void publicoSinToken(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isOk());
    }

    @ParameterizedTest(name = "GET {0} responde con cualquier rol y con JWT sin roles")
    @MethodSource("publicos")
    void publicoConCualquierRol(String ruta) throws Exception {
        for (String rol : ROLES) {
            mvc.perform(get(ruta).with(conRoles(rol))).andExpect(status().isOk());
        }
        mvc.perform(get(ruta).with(conRoles())).andExpect(status().isOk());
    }

    // ---- eventos recibidos: solo administrador ----

    @Test
    void eventosRecibidosSoloAdministrador() throws Exception {
        for (String rol : ROLES) {
            var accion = mvc.perform(get(EVENTOS).with(conRoles(rol)));
            if (rol.equals("administrador")) {
                accion.andExpect(status().isOk());
            } else {
                accion.andExpect(status().isForbidden());
            }
        }
    }

    @Test
    void eventosRecibidosSinRolesDa403YSinTokenDa401() throws Exception {
        mvc.perform(get(EVENTOS).with(conRoles())).andExpect(status().isForbidden());
        mvc.perform(get(EVENTOS).with(conRoles("offline_access"))).andExpect(status().isForbidden());
        mvc.perform(get(EVENTOS)).andExpect(status().isUnauthorized());
    }

    @Test
    void elRolEsExactoYNoAdmiteVariantes() throws Exception {
        for (String rol : List.of("ADMINISTRADOR", "administrador ", "ROLE_administrador", "Administrador")) {
            mvc.perform(get(EVENTOS).with(conRoles(rol))).andExpect(status().isForbidden());
        }
    }

    // ---- denyAll ----

    @ParameterizedTest(name = "{0} {1} -> 401 sin token")
    @MethodSource("noListados")
    void noListadoSinTokenDa401(String metodo, String ruta) throws Exception {
        mvc.perform(llamada(metodo, ruta)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} {1} -> 403 con administrador")
    @MethodSource("noListados")
    void noListadoSeDeniegaAunqueHayaRol(String metodo, String ruta) throws Exception {
        mvc.perform(llamada(metodo, ruta).with(conRoles("administrador"))).andExpect(status().isForbidden());
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> noListados() {
        return Stream.of(
                args("POST", "/api/publica/fichas"),
                args("PUT", FICHA),
                args("PATCH", FICHA),
                args("DELETE", FICHA),
                args("POST", "/api/publica/ping"),
                args("POST", EVENTOS),
                args("GET", "/api/publica/eventos"),
                args("GET", "/api/publica/eventos/otro"),
                args("GET", "/api/publica/otra"),
                args("GET", FICHA + "/extra"),
                args("GET", "/api/cualquiercosa"),
                args("GET", "/api/cientifico/ping"),
                args("GET", "/api/v1/externa/fichas"),
                args("GET", "/actuator/env"),
                args("GET", "/"));
    }

    private static org.junit.jupiter.params.provider.Arguments args(String metodo, String ruta) {
        return org.junit.jupiter.params.provider.Arguments.of(metodo, ruta);
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder llamada(String metodo,
                                                                                                   String ruta) {
        return switch (metodo) {
            case "POST" -> post(ruta);
            case "PUT" -> put(ruta);
            case "PATCH" -> patch(ruta);
            case "DELETE" -> delete(ruta);
            default -> get(ruta);
        };
    }

    @Test
    void healthEstaAbiertoSinToken() throws Exception {
        // No hay actuator en esta capa web: lo relevante es que no responda 401/403
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    // ---- SEC-J-20 / OBS-J17-1: 403 problem+json ROL_INSUFICIENTE y 401 del contrato (04 §2 y §5.1) ----

    /** 403 ROL_INSUFICIENTE: problem+json, no-store, cuerpo fijo y sin datos internos. */
    private static ResultActions esRolInsuficiente(ResultActions accion, String ruta) throws Exception {
        return accion.andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.codigo").value("ROL_INSUFICIENTE"))
                .andExpect(jsonPath("$.instance").value(ruta))
                .andExpect(jsonPath("$.detail").value("No tienes permiso para ver esta información."));
    }

    /** 401 como en el contrato (§2): sin cuerpo, WWW-Authenticate: Bearer y no-store. */
    private static ResultActions esNoAutenticado(ResultActions accion, String ruta) throws Exception {
        return accion.andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, org.hamcrest.Matchers.startsWith("Bearer")))
                .andExpect(content().string(""));
    }

    @Test
    void eventosRecibidosConRolNoAdministradorDa403ProblemJson() throws Exception {
        for (String rol : ROLES) {
            if (!rol.equals("administrador")) {
                esRolInsuficiente(mvc.perform(get(EVENTOS).with(conRoles(rol))), EVENTOS);
            }
        }
        esRolInsuficiente(mvc.perform(get(EVENTOS).with(conRoles())), EVENTOS);
        esRolInsuficiente(mvc.perform(get(EVENTOS).with(conRoles("offline_access"))), EVENTOS);
    }

    @Test
    void eventosRecibidosSinTokenDa401SinCuerpo() throws Exception {
        esNoAutenticado(mvc.perform(get(EVENTOS)), EVENTOS);
    }

    @ParameterizedTest(name = "{0} {1} -> 401 sin cuerpo sin token")
    @MethodSource("noListados")
    void noListadoSinTokenDa401SinCuerpo(String metodo, String ruta) throws Exception {
        esNoAutenticado(mvc.perform(llamada(metodo, ruta)), ruta);
    }

    @ParameterizedTest(name = "{0} {1} -> 403 problem+json con administrador (denyAll)")
    @MethodSource("noListados")
    void noListadoDa403ProblemJsonAunqueHayaRol(String metodo, String ruta) throws Exception {
        esRolInsuficiente(mvc.perform(llamada(metodo, ruta).with(conRoles("administrador"))), ruta);
    }

    @Test
    void unJwtInvalidoDa401SinCuerpoSinFiltrarElMotivoInterno() throws Exception {
        when(jwtDecoder.decode("token-malo")).thenThrow(new BadJwtException("firma rota en el nodo interno-7"));
        var respuesta = esNoAutenticado(mvc.perform(get(EVENTOS)
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-malo")), EVENTOS)
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andReturn().getResponse();
        assertThat(respuesta.getContentAsString()).doesNotContain("interno-7").doesNotContain("firma rota");
        assertThat(respuesta.getHeader(HttpHeaders.WWW_AUTHENTICATE)).doesNotContain("interno-7");
    }

    @Test
    void unTokenInvalidoEnUnaRutaPublicaTambienDa401SinCuerpo() throws Exception {
        // contrato 04 §2: en /api/publica/** el 401 solo ocurre si se envía un Authorization inválido
        when(jwtDecoder.decode("token-malo")).thenThrow(new BadJwtException("x"));
        esNoAutenticado(mvc.perform(get("/api/publica/ping").header(HttpHeaders.AUTHORIZATION, "Bearer token-malo")),
                "/api/publica/ping");
    }

    @Test
    void unAccessDeniedExceptionDeUnEndpointDeFichasNoSeConvierteEn500() throws Exception {
        // ConsultaPublicaExceptionHandler captura Exception: un 403 de @PreAuthorize debe llegar a Spring Security
        when(servicio.buscar(any())).thenThrow(new AccessDeniedException("detalle interno"));
        var respuesta = esRolInsuficiente(mvc.perform(get("/api/publica/fichas").with(conRoles())),
                "/api/publica/fichas").andReturn().getResponse();
        assertThat(respuesta.getContentAsString()).doesNotContain("detalle interno");
    }

    @Test
    void elCuerpoDel403NoFiltraTrazasNiDetallesInternos() throws Exception {
        String cuerpo = mvc.perform(get(EVENTOS).with(conRoles("investigador_campo")))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("Exception").doesNotContain("org.springframework").doesNotContain("at co.edu")
                .doesNotContain("hasRole").doesNotContain("PreAuthorize").doesNotContain("administrador");
    }
}
