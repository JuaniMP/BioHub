package co.edu.unbosque.biohub.consultapublica;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.consultapublica.config.SecurityConfig;
import co.edu.unbosque.biohub.sondaseguridad.MocksDeDependencias;
import co.edu.unbosque.biohub.sondaseguridad.VerificadorDeEndpoints;
import co.edu.unbosque.biohub.sondaseguridad.VerificadorDeEndpoints.Registrado;

/**
 * SEC-J-22 y SEC-J-I22: prueba de comportamiento sobre TODOS los endpoints de servicio-consulta-publica. Carga todos los
 * controladores del paquete base ({@code @WebMvcTest} sin lista; sus servicios se simulan solos con
 * {@link MocksDeDependencias}), recorre cada mapping registrado en Spring MVC y exige que un JWT sin roles reciba
 * 403 y nunca ejecute el método. Las únicas excepciones son las tres rutas públicas de {@link #RUTAS_PUBLICAS}; las demás, incluido
 * lo que hoy deniega {@code denyAll()}, deben dar 403 a un JWT sin roles.
 * Si mañana aparece un endpoint sin control (por la vía que sea), esta prueba falla aunque
 * {@code ArquitecturaSeguridadTest} no conozca esa vía.
 */
@WebMvcTest
@Import({SecurityConfig.class, MocksDeDependencias.class})
class TodosLosEndpointsExigenRolTest {

    /**
     * Rutas públicas explícitas (HU-01, método + patrón exactos): lo que {@code SecurityConfig} deja en
     * {@code permitAll()}. Un endpoint nuevo que no esté aquí debe dar 403 a un JWT sin roles.
     */
    static final Set<String> RUTAS_PUBLICAS = Set.of("GET /api/publica/ping", "GET /api/publica/fichas",
            "GET /api/publica/fichas/{id}");

    @Autowired
    private ApplicationContext ctx;
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("el contexto expone los endpoints esperados (la prueba no es vacua)")
    void elRecorridoNoEstaVacio() {
        List<String> ids = VerificadorDeEndpoints.registrados(ctx).stream()
                .filter(Registrado::esDeLaAplicacion).map(Registrado::id).toList();
        assertThat(ids).hasSizeGreaterThanOrEqualTo(4)
                .contains("PingController.ping", "FichasPublicasController.buscar", "FichasPublicasController.ficha",
                        "EventosRecibidosController.recibidos");
    }

    @Test
    @DisplayName("HTTP: todo mapping responde 403 a un JWT sin roles (salvo 400/406/415 no concluyentes)")
    void todoMappingDa403AUnJwtSinRoles() throws Exception {
        List<String> violaciones = new ArrayList<>();
        for (Registrado endpoint : VerificadorDeEndpoints.registrados(ctx)) {
            if (!endpoint.esPublico(RUTAS_PUBLICAS)) {
                violaciones.addAll(VerificadorDeEndpoints.peticionHttp(mvc, endpoint));
            }
        }
        assertThat(violaciones).as("endpoints que responden a un JWT sin roles algo distinto de 403").isEmpty();
    }

    @Test
    @DisplayName("invocación: ningún método de endpoint se ejecuta con un JWT sin roles (AccessDeniedException)")
    void ningunMetodoSeEjecutaConUnJwtSinRoles() {
        List<String> violaciones = VerificadorDeEndpoints.registrados(ctx).stream()
                .filter(Registrado::esDeLaAplicacion)
                .filter(e -> !e.esPublico(RUTAS_PUBLICAS))
                .map(e -> VerificadorDeEndpoints.invocacionDirecta(ctx, e))
                .flatMap(java.util.Optional::stream)
                .toList();
        assertThat(violaciones).as("endpoints sin control de rol efectivo").isEmpty();
    }

    @Test
    @DisplayName("SEC-J-I22: la seguridad por método sigue activa en todos los controladores (proxy de @PreAuthorize)")
    void laSeguridadPorMetodoSigueActiva() {
        // la invocación directa ya falla si un bean no tiene proxy; esto da un mensaje propio y fija el motivo
        List<String> sinProxy = VerificadorDeEndpoints.registrados(ctx).stream()
                .filter(Registrado::esDeLaAplicacion)
                .filter(e -> !java.lang.reflect.Modifier.isStatic(e.metodo().getMethod().getModifiers()))
                .filter(e -> !org.springframework.aop.support.AopUtils.isAopProxy(
                        e.metodo().getBean() instanceof String n ? ctx.getBean(n) : e.metodo().getBean()))
                .map(Registrado::id).toList();
        assertThat(sinProxy).as("controladores sin proxy de seguridad por método (@EnableMethodSecurity)").isEmpty();
    }

    @Test
    @DisplayName("las rutas públicas son exactamente las de PUBLICOS_PERMIT_ALL y responden sin 401/403, con y sin token")
    void soloLasRutasPublicasExplicitasEstanAbiertas() throws Exception {
        List<Registrado> publicos = VerificadorDeEndpoints.registrados(ctx).stream()
                .filter(e -> e.esPublico(RUTAS_PUBLICAS)).toList();
        // los métodos públicos coinciden con la lista que ArquitecturaSeguridadTest deja usar permitAll()
        assertThat(publicos.stream().map(Registrado::id).collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(ArquitecturaSeguridadTest.PUBLICOS_PERMIT_ALL);
        // y todas las rutas explícitas existen (sin entradas huérfanas)
        assertThat(publicos.stream().flatMap(e -> e.rutas().stream()).collect(java.util.stream.Collectors.toSet()))
                .isEqualTo(RUTAS_PUBLICAS);
        for (Registrado e : publicos) {
            String ruta = VerificadorDeEndpoints.rutaConcreta(e, e.patrones().get(0));
            assertThat(mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(ruta))
                    .andReturn().getResponse().getStatus()).as("GET %s sin token", ruta).isNotIn(401, 403);
            assertThat(VerificadorDeEndpoints.estadoSinRoles(mvc, e, "GET", e.patrones().get(0)))
                    .as("GET %s con JWT sin roles", ruta).isNotIn(401, 403);
        }
    }

    @Test
    @DisplayName("no hay rutas funcionales ni handlers por URL fuera de @RequestMapping")
    void noHayOtrasViasDeExponerRutas() {
        assertThat(VerificadorDeEndpoints.otrasVias(ctx)).isEmpty();
    }
}
