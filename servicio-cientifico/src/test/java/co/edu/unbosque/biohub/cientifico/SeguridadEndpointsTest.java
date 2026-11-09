package co.edu.unbosque.biohub.cientifico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import co.edu.unbosque.biohub.cientifico.config.SecurityConfig;
import co.edu.unbosque.biohub.cientifico.controller.PingController;
import co.edu.unbosque.biohub.cientifico.eventos.controller.EventosPruebaController;
import co.edu.unbosque.biohub.cientifico.eventos.service.PublicadorEventos;
import co.edu.unbosque.biohub.cientifico.ubicacion.controller.BusquedaHallazgosController;
import co.edu.unbosque.biohub.cientifico.ubicacion.controller.MapaUbicacionController;
import co.edu.unbosque.biohub.cientifico.ubicacion.controller.RecifradoController;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.BusquedaHallazgosService;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.MapaService;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.PoliticaAccesoUbicacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.RecifradoService;

/**
 * Matriz endpoint x rol de servicio-cientifico (11-seguridad-y-acceso.md §2) y denyAll de la cadena de filtros.
 * Capa web con servicios simulados; sin BD ni RabbitMQ.
 */
@WebMvcTest({PingController.class, EventosPruebaController.class, BusquedaHallazgosController.class,
        MapaUbicacionController.class, RecifradoController.class})
@Import({SecurityConfig.class, PoliticaAccesoUbicacion.class})
class SeguridadEndpointsTest {

    static final String INVESTIGADOR = "investigador_campo";
    static final String AUDITOR = "auditor_cientifico";
    static final String ALTO_RANGO = "cientifico_alto_rango";
    static final String ANALISTA = "analista_ambiental";
    static final String ADMIN = "administrador";
    static final List<String> LOS_5 = List.of(INVESTIGADOR, AUDITOR, ALTO_RANGO, ANALISTA, ADMIN);

    /** metodo, ruta y roles que la matriz permite. */
    record Endpoint(HttpMethod metodo, String ruta, Set<String> permitidos) {
        @Override
        public String toString() {
            return metodo + " " + ruta;
        }
    }

    static final List<Endpoint> ENDPOINTS = List.of(
            new Endpoint(HttpMethod.GET, "/api/cientifico/ping", Set.copyOf(LOS_5)), // fuera de la matriz: conserva el comportamiento
            new Endpoint(HttpMethod.GET, "/api/cientifico/ubicacion/hallazgos", Set.copyOf(LOS_5)),
            new Endpoint(HttpMethod.GET, "/api/cientifico/ubicacion/mapa", Set.of(ALTO_RANGO)),
            new Endpoint(HttpMethod.POST, "/api/cientifico/ubicacion/recifrado", Set.of(ADMIN)),
            new Endpoint(HttpMethod.POST, "/api/cientifico/eventos/prueba", Set.of(ADMIN))); // fuera de la matriz

    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder jwtDecoder;
    @MockitoBean
    private BusquedaHallazgosService busqueda;
    @MockitoBean
    private MapaService mapa;
    @MockitoBean
    private RecifradoService recifrado;
    @MockitoBean
    private PublicadorEventos publicador;

    private static RequestPostProcessor conRoles(String... roles) {
        return jwt().jwt(j -> j.claim("preferred_username", "usuario1"))
                .authorities(Stream.of(roles).map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r)).toList());
    }

    private static MockHttpServletRequestBuilder peticion(HttpMethod metodo, String ruta) {
        return MockMvcRequestBuilders.request(metodo, ruta);
    }

    // ---- endpoint x rol ----

    static Stream<Arguments> endpointPorRol() {
        List<Arguments> casos = new ArrayList<>();
        for (Endpoint e : ENDPOINTS) {
            for (String rol : LOS_5) {
                casos.add(Arguments.of(e, rol, e.permitidos().contains(rol)));
            }
        }
        return casos.stream();
    }

    @ParameterizedTest(name = "{0} con {1}: permitido={2}")
    @MethodSource("endpointPorRol")
    void cadaRolRecibeLoQueDiceLaMatriz(Endpoint e, String rol, boolean permitido) throws Exception {
        var accion = mvc.perform(peticion(e.metodo(), e.ruta()).with(conRoles(rol)));
        if (permitido) {
            accion.andExpect(status().is2xxSuccessful());
        } else {
            accion.andExpect(status().isForbidden());
        }
    }

    static Stream<Endpoint> endpoints() {
        return ENDPOINTS.stream();
    }

    @ParameterizedTest(name = "{0} sin token -> 401")
    @MethodSource("endpoints")
    void sinTokenDa401(Endpoint e) throws Exception {
        mvc.perform(peticion(e.metodo(), e.ruta())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest(name = "{0} con JWT sin roles -> 403")
    @MethodSource("endpoints")
    void autenticadoSinRolesDa403(Endpoint e) throws Exception {
        mvc.perform(peticion(e.metodo(), e.ruta()).with(conRoles())).andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} con rol ajeno al negocio (offline_access) -> 403")
    @MethodSource("endpoints")
    void rolAjenoAlNegocioDa403(Endpoint e) throws Exception {
        mvc.perform(peticion(e.metodo(), e.ruta()).with(conRoles("offline_access", "default-roles-biohub")))
                .andExpect(status().isForbidden());
    }

    @Test
    void elRolEsExactoYNoAdmiteVariantes() throws Exception {
        for (String rol : List.of("ADMINISTRADOR", "Cientifico_Alto_Rango", "administrador ", "ROLE_administrador")) {
            mvc.perform(post("/api/cientifico/ubicacion/recifrado").with(conRoles(rol))).andExpect(status().isForbidden());
            mvc.perform(get("/api/cientifico/ubicacion/mapa").with(conRoles(rol))).andExpect(status().isForbidden());
        }
    }

    @Test
    void administradorNoRecibeElMapaYAltoRangoNoRecifra() throws Exception {
        mvc.perform(get("/api/cientifico/ubicacion/mapa").with(conRoles(ADMIN))).andExpect(status().isForbidden());
        mvc.perform(post("/api/cientifico/ubicacion/recifrado").with(conRoles(ALTO_RANGO))).andExpect(status().isForbidden());
        mvc.perform(post("/api/cientifico/ubicacion/recifrado").with(conRoles(INVESTIGADOR))).andExpect(status().isForbidden());
    }

    @Test
    void elRol403SinRolesConservaElProblemJsonDelContrato() throws Exception {
        // contrato 04 §5.1: 403 ROL_INSUFICIENTE (la seguridad por URL no debe reemplazarlo por un 403 vacío)
        mvc.perform(get("/api/cientifico/ubicacion/hallazgos").with(conRoles()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ROL_INSUFICIENTE"));
    }

    // ---- denyAll ----

    @ParameterizedTest(name = "GET {0} con administrador (fuera de /api/cientifico/**) -> 403")
    @ValueSource(strings = {"/api/cualquiercosa", "/", "/api/publica/fichas", "/api/publica/ping", "/api/equipos/activos",
            "/api/reportes/x", "/api/cientificoX/ping", "/otra/ruta", "/actuator/env", "/actuator"})
    void rutaFueraDelPrefijoSeDeniegaAunqueHayaRol(String ruta) throws Exception {
        mvc.perform(get(ruta).with(conRoles(LOS_5.toArray(String[]::new)))).andExpect(status().isForbidden());
        mvc.perform(post(ruta).with(conRoles(ADMIN))).andExpect(status().isForbidden());
        mvc.perform(delete(ruta).with(conRoles(ADMIN))).andExpect(status().isForbidden());
    }

    @Test
    void rutaFueraDelPrefijoSinTokenDa401() throws Exception {
        mvc.perform(get("/api/cualquiercosa")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/publica/fichas")).andExpect(status().isUnauthorized());
    }

    @Test
    void elPrefijoCientificoNoSeBloqueaPorUrlParaLosFuturosEndpoints() throws Exception {
        // Sin controlador responde 404 (no 403): el URL deja pasar a quien tenga un JWT y el rol lo decide
        // el @PreAuthorize de cada endpoint (Hernando).
        mvc.perform(get("/api/cientifico/hallazgos").with(conRoles(INVESTIGADOR))).andExpect(status().isNotFound());
        mvc.perform(get("/api/cientifico/ubicacion/inexistente").with(conRoles(ADMIN))).andExpect(status().isNotFound());
    }

    @Test
    void healthEstaAbiertoSinToken() throws Exception {
        // No hay actuator en esta capa web: lo relevante es que no responda 401/403
        mvc.perform(get("/actuator/health")).andExpect(status().isNotFound());
    }

    // ---- SEC-J-20: 403 problem+json ROL_INSUFICIENTE y 401 del contrato en todo el servicio (04 §2 y §5.1) ----

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

    @ParameterizedTest(name = "{0} con JWT sin roles -> 403 problem+json ROL_INSUFICIENTE")
    @MethodSource("endpoints")
    void todoEndpointDa403ProblemJsonConJwtSinRoles(Endpoint e) throws Exception {
        esRolInsuficiente(mvc.perform(peticion(e.metodo(), e.ruta()).with(conRoles())), e.ruta());
    }

    @ParameterizedTest(name = "{0} con un rol que la matriz no permite -> 403 problem+json ROL_INSUFICIENTE")
    @MethodSource("endpointPorRol")
    void todoEndpointDa403ProblemJsonConRolNoPermitido(Endpoint e, String rol, boolean permitido) throws Exception {
        if (!permitido) {
            esRolInsuficiente(mvc.perform(peticion(e.metodo(), e.ruta()).with(conRoles(rol))), e.ruta());
        }
    }

    @ParameterizedTest(name = "{0} sin token -> 401 sin cuerpo")
    @MethodSource("endpoints")
    void todoEndpointDa401SinCuerpoSinToken(Endpoint e) throws Exception {
        esNoAutenticado(mvc.perform(peticion(e.metodo(), e.ruta())), e.ruta());
    }

    @Test
    void pingYEventosPruebaFueraDeUbicacionDan403ProblemJsonSinRol() throws Exception {
        // OBS-J17-1 / SEC-J-20: antes salían con cuerpo vacío; el rol lo comprueba @PreAuthorize, no la URL
        esRolInsuficiente(mvc.perform(get("/api/cientifico/ping").with(conRoles())), "/api/cientifico/ping");
        esRolInsuficiente(mvc.perform(post("/api/cientifico/eventos/prueba").with(conRoles(INVESTIGADOR))),
                "/api/cientifico/eventos/prueba");
    }

    @ParameterizedTest(name = "{0} fuera de /api/cientifico/** con todos los roles -> 403 problem+json")
    @ValueSource(strings = {"/api/cualquiercosa", "/", "/api/publica/fichas", "/api/equipos/activos", "/otra/ruta",
            "/actuator/env", "/actuator"})
    void denyAllDa403ProblemJsonYSinTokenDa401SinCuerpo(String ruta) throws Exception {
        esRolInsuficiente(mvc.perform(get(ruta).with(conRoles(LOS_5.toArray(String[]::new)))), ruta);
        esRolInsuficiente(mvc.perform(post(ruta).with(conRoles(ADMIN))), ruta);
        esNoAutenticado(mvc.perform(get(ruta)), ruta);
    }

    @Test
    void unJwtInvalidoDa401SinCuerpoSinFiltrarElMotivoInterno() throws Exception {
        when(jwtDecoder.decode("token-malo")).thenThrow(new BadJwtException("firma rota en el nodo interno-7"));
        var resultado = esNoAutenticado(mvc.perform(get("/api/cientifico/ping")
                .header(HttpHeaders.AUTHORIZATION, "Bearer token-malo")), "/api/cientifico/ping")
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\""))
                .andReturn().getResponse();
        assertThat(resultado.getContentAsString()).doesNotContain("interno-7").doesNotContain("firma rota");
        assertThat(resultado.getHeader(HttpHeaders.WWW_AUTHENTICATE)).doesNotContain("interno-7");
    }

    @Test
    void elCuerpoDel403NoFiltraTrazasNiDetallesInternos() throws Exception {
        String cuerpo = mvc.perform(get("/api/cientifico/ubicacion/mapa").with(conRoles(ADMIN)))
                .andReturn().getResponse().getContentAsString();
        String cuerpoPing = mvc.perform(get("/api/cientifico/ping").with(conRoles()))
                .andReturn().getResponse().getContentAsString();
        for (String c : List.of(cuerpo, cuerpoPing)) {
            assertThat(c).doesNotContain("Exception").doesNotContain("org.springframework").doesNotContain("at co.edu")
                    .doesNotContain("hasRole").doesNotContain("PreAuthorize").doesNotContain("administrador");
        }
    }
}
