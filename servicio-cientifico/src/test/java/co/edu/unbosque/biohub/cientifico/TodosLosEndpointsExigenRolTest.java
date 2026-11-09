package co.edu.unbosque.biohub.cientifico;

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

import co.edu.unbosque.biohub.cientifico.config.SecurityConfig;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.PoliticaAccesoUbicacion;
import co.edu.unbosque.biohub.sondaseguridad.MocksDeDependencias;
import co.edu.unbosque.biohub.sondaseguridad.VerificadorDeEndpoints;
import co.edu.unbosque.biohub.sondaseguridad.VerificadorDeEndpoints.Registrado;

/**
 * SEC-J-22 y SEC-J-I22: prueba de comportamiento sobre TODOS los endpoints de servicio-cientifico. Carga todos los
 * controladores del paquete base ({@code @WebMvcTest} sin lista; sus servicios se simulan solos con
 * {@link MocksDeDependencias}), recorre cada mapping registrado en Spring MVC y exige que un JWT sin roles reciba
 * 403 y nunca ejecute el método. Aquí no hay rutas públicas: todo {@code /api/cientifico/**} exige rol (D55).
 * Si mañana aparece un endpoint sin control (por la vía que sea), esta prueba falla aunque
 * {@code ArquitecturaSeguridadTest} no conozca esa vía.
 */
@WebMvcTest
@Import({SecurityConfig.class, PoliticaAccesoUbicacion.class, MocksDeDependencias.class})
class TodosLosEndpointsExigenRolTest {

    /** Rutas que no exigen rol (método + patrón exactos). En servicio-cientifico, ninguna. */
    static final Set<String> RUTAS_PUBLICAS = Set.of();

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
        assertThat(ids).hasSizeGreaterThanOrEqualTo(5)
                .contains("PingController.ping", "MapaUbicacionController.mapa");
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
    @DisplayName("no hay rutas funcionales ni handlers por URL fuera de @RequestMapping")
    void noHayOtrasViasDeExponerRutas() {
        assertThat(VerificadorDeEndpoints.otrasVias(ctx)).isEmpty();
    }
}
