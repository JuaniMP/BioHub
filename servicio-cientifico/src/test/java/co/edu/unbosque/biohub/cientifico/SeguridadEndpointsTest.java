package co.edu.unbosque.biohub.cientifico;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.HttpMethod;
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
}
