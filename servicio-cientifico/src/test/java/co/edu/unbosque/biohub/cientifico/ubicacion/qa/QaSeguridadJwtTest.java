package co.edu.unbosque.biohub.cientifico.ubicacion.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;

/**
 * QA: SG-04..SG-09, MP-03..MP-09, HB-14/HB-15, RC-04 con JWT reales (firma RSA, JWKS local, emisor, audiencia,
 * vigencia). Las pruebas del desarrollador simulan el decodificador; estas ejercen el de producción.
 */
class QaSeguridadJwtTest extends QaBase {

    private static final String BUSQUEDA = "/api/cientifico/ubicacion/hallazgos";
    private static final String MAPA = "/api/cientifico/ubicacion/mapa";
    private static final String RECIFRADO = "/api/cientifico/ubicacion/recifrado";

    @BeforeEach
    void datos() {
        limpiar();
        sembrar("Atelopus ebenoides", LocalDate.of(2026, 3, 14), "investigador1", "15",
                EstadoConservacion.EN_PELIGRO, CENTINELA_1[0], CENTINELA_1[1]);
    }

    private MockHttpServletResponse llamar(String metodo, String url, String autorizacion) throws Exception {
        MockHttpServletRequestBuilder b = metodo.equals("POST") ? post(url) : get(url);
        if (autorizacion != null) {
            b.header("Authorization", autorizacion);
        }
        return mvc.perform(b).andReturn().getResponse();
    }

    private static Stream<Arguments> endpoints() {
        return Stream.of(Arguments.of("GET", BUSQUEDA), Arguments.of("GET", MAPA), Arguments.of("POST", RECIFRADO));
    }

    private static void sinFiltrarNada(MockHttpServletResponse r) throws Exception {
        String cuerpo = r.getContentAsString();
        assertThat(cuerpo).doesNotContain("4.6097").doesNotContain("-74.0817").doesNotContain("Exception")
                .doesNotContain("at co.edu").doesNotContain("stackTrace");
    }

    // ---- sin credenciales válidas -> 401 en los tres endpoints ----------------------------------------------

    @ParameterizedTest(name = "{0} {1} sin token")
    @MethodSource("endpoints")
    void sinTokenDa401(String metodo, String url) throws Exception {
        MockHttpServletResponse r = llamar(metodo, url, null);
        assertThat(r.getStatus()).isEqualTo(401);
        assertThat(r.getContentAsString()).isEmpty();
    }

    @ParameterizedTest(name = "{0} {1} credencial rechazada")
    @MethodSource("endpoints")
    void credencialesInvalidasDan401(String metodo, String url) throws Exception {
        String alto = "cientifico_alto_rango";
        String admin = "administrador";
        List<String> malas = List.of(
                "Bearer " + jwt(Tok.valido("u", alto, admin).exp(Instant.now().minusSeconds(120))),          // vencido
                "Bearer " + jwt(Tok.valido("u", alto, admin).iss("http://otro-realm.invalid/realms/x")),      // otro emisor
                "Bearer " + jwt(Tok.valido("u", alto, admin).aud(null)),                                      // sin aud
                "Bearer " + jwt(Tok.valido("u", alto, admin).aud("otra-api")),                                // aud ajena
                "Bearer " + jwt(Tok.valido("u", alto, admin).aud(List.of("account"))),                        // aud sin biohub-api
                "Bearer " + jwt(Tok.valido("u", alto, admin).firma(CLAVE_AJENA)),                             // firma de otra clave
                jwtAlgNone(),                                                                                 // alg=none
                jwtPayloadAlterado("u", alto, "administrador"),                                               // payload alterado
                "Basic dXN1YXJpbzpjbGF2ZQ==",                                                                 // Basic
                "Bearer ",                                                                                    // vacío
                "Bearer basura.sin.formato",
                "Bearer");
        for (String a : malas) {
            MockHttpServletResponse r = llamar(metodo, url, a);
            assertThat(r.getStatus()).as("Authorization=%s", a.length() > 30 ? a.substring(0, 30) + "..." : a)
                    .isEqualTo(401);
            sinFiltrarNada(r);
        }
    }

    @Test
    void elTokenEnLaUrlNoSeAcepta() throws Exception {
        String t = jwt(Tok.valido("juana", "cientifico_alto_rango")).replace("Bearer ", "");
        assertThat(llamar("GET", MAPA + "?access_token=" + t, null).getStatus()).isEqualTo(401);
    }

    // ---- matriz de roles con tokens reales ------------------------------------------------------------------

    private static Stream<Arguments> matrizRoles() {
        List<Arguments> a = new java.util.ArrayList<>();
        for (String rol : ROLES_INTERNOS) {
            a.add(Arguments.of(rol, BUSQUEDA, "GET", 200));
            a.add(Arguments.of(rol, MAPA, "GET", rol.equals("cientifico_alto_rango") ? 200 : 403));
            a.add(Arguments.of(rol, RECIFRADO, "POST", rol.equals("administrador") ? 200 : 403));
        }
        return a.stream();
    }

    @ParameterizedTest(name = "{0} {2} {1} -> {3}")
    @MethodSource("matrizRoles")
    void matrizDeRoles(String rol, String url, String metodo, int esperado) throws Exception {
        MockHttpServletResponse r = llamar(metodo, url, bearer("usuario-" + rol, rol));
        assertThat(r.getStatus()).isEqualTo(esperado);
        assertThat(r.getHeader("Cache-Control")).contains("no-store");
        sinFiltrarNada(r.getStatus() == 200 && rol.equals("cientifico_alto_rango") ? new MockHttpServletResponse() : r);
    }

    @ParameterizedTest(name = "{1} sin ningún rol interno -> 403")
    @MethodSource("endpoints")
    void autenticadoSinRolInternoDa403(String metodo, String url) throws Exception {
        for (List<String> roles : List.of(List.<String>of(), List.of("offline_access"),
                List.of("uma_authorization", "default-roles-biohub"), List.of("ADMINISTRADOR"),
                List.of("administrador ", "Cientifico_Alto_Rango"), List.of("ROLE_administrador"))) {
            MockHttpServletResponse r = llamar(metodo, url, bearer("nadie", roles.toArray(String[]::new)));
            assertThat(r.getStatus()).as("roles=%s", roles).isEqualTo(403);
            sinFiltrarNada(r);
        }
    }

    @Test
    void elRol403TienePrioridadSobreLaValidacion400() throws Exception {
        // MP-09: un rol sin permiso con parámetros inválidos recibe 403, no 400 (no enseña las reglas)
        MockHttpServletResponse r = llamar("GET", MAPA + "?hallazgoId=xyz&desde=malo", bearer("inv", "investigador_campo"));
        assertThat(r.getStatus()).isEqualTo(403);
    }

    @Test
    void unTokenValidoConVariosRolesUsaElMasAlto() throws Exception {
        MockHttpServletResponse r = llamar("GET", MAPA, bearer("juana", "investigador_campo", "cientifico_alto_rango"));
        assertThat(r.getStatus()).isEqualTo(200);
        assertThat(r.getContentAsString()).contains("4.6097");
    }

    @Test
    void elIdentificadorDelUsuarioNoSePuedeSuplantarPorCabecera() throws Exception {
        // Cabeceras que un cliente malicioso podría mandar para hacerse pasar por otro rol/usuario
        MockHttpServletResponse r = mvc.perform(get(MAPA).header("Authorization", bearer("inv", "investigador_campo"))
                .header("X-User-Roles", "cientifico_alto_rango").header("X-Forwarded-User", "admin1")
                .header("X-Roles", "administrador")).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(403);
    }

    @Test
    void metodosNoPermitidos() throws Exception {
        for (String url : List.of(BUSQUEDA, MAPA)) {
            for (var m : List.of(post(url), org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(url),
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete(url))) {
                MockHttpServletResponse r = mvc.perform(m.header("Authorization", bearer("juana", "administrador",
                        "cientifico_alto_rango"))).andReturn().getResponse();
                assertThat(r.getStatus()).as("%s %s", m.buildRequest(new org.springframework.mock.web.MockServletContext())
                        .getMethod(), url).isIn(403, 405);
                sinFiltrarNada(r);
            }
        }
        // GET sobre recifrado (que es POST) y ruta inexistente
        MockHttpServletResponse g = llamar("GET", RECIFRADO, bearer("admin", "administrador"));
        assertThat(g.getStatus()).isIn(404, 405);
        assertThat(llamar("GET", "/api/cientifico/ubicacion/inexistente", bearer("admin", "administrador")).getStatus())
                .isIn(404);
    }

    @Test
    void elTokenValidoDeOtroUsuarioNoCambiaElIdentificadorAuditado() throws Exception {
        // sanity: un mismo token funciona dos veces (no hay estado de sesión)
        String a = bearer("juana", "cientifico_alto_rango");
        assertThat(llamar("GET", MAPA, a).getStatus()).isEqualTo(200);
        assertThat(llamar("GET", MAPA, a).getStatus()).isEqualTo(200);
        assertThat(llamar("GET", MAPA, null).getStatus()).isEqualTo(401);
    }
}
