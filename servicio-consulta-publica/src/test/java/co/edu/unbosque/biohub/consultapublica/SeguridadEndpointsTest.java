package co.edu.unbosque.biohub.consultapublica;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
}
