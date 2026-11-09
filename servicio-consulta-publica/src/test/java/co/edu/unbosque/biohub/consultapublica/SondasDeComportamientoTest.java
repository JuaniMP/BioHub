package co.edu.unbosque.biohub.consultapublica;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
import co.edu.unbosque.biohub.sondaseguridad.SondasElusion;
import co.edu.unbosque.biohub.sondaseguridad.VerificadorDeEndpoints;
import co.edu.unbosque.biohub.sondaseguridad.VerificadorDeEndpoints.Registrado;

/**
 * Control de {@code TodosLosEndpointsExigenRolTest} (SEC-J-22): carga a propósito las sondas que Spring registra como
 * endpoint y que las reglas de forma no veían ({@code @GetExchange}, {@code @HttpExchange}, interfaz de exchange,
 * método {@code final}, método {@code static}) más un endpoint sin control, y exige que la prueba de comportamiento
 * las marque todas, y que un controlador correcto no se marque. Sin esto, la prueba podría pasar siempre sin
 * demostrar que detecta nada. Las sondas viven en {@code sondaseguridad}, fuera del paquete escaneado.
 */
@WebMvcTest(controllers = SondasElusion.ControladorCorrecto.class) // solo esta (fuera del escaneo); el resto se importa a mano
@Import({SecurityConfig.class,
        SondasElusion.ConGetExchange.class, SondasElusion.ConGetExchangeYRol.class,
        SondasElusion.ConHttpExchange.class, SondasElusion.ImplementaInterfazHttpExchange.class,
        SondasElusion.MetodoFinal.class, SondasElusion.MetodoStatic.class, SondasElusion.SinControl.class,
        SondasElusion.ControladorCorrecto.class})
class SondasDeComportamientoTest {

    @Autowired
    private ApplicationContext ctx;
    @Autowired
    private MockMvc mvc;
    @MockitoBean
    private JwtDecoder jwtDecoder;

    /** Violaciones (HTTP e invocación directa) por endpoint, igual que en la prueba real. */
    private List<String> violaciones(String controlador) throws Exception {
        List<String> v = new ArrayList<>();
        for (Registrado e : VerificadorDeEndpoints.registrados(ctx)) {
            if (e.metodo().getBeanType().getSimpleName().equals(controlador)) {
                v.addAll(VerificadorDeEndpoints.peticionHttp(mvc, e));
                VerificadorDeEndpoints.invocacionDirecta(ctx, e).ifPresent(v::add);
            }
        }
        return v;
    }

    @Test
    @DisplayName("las sondas están registradas como endpoints (si no, el control no demuestra nada)")
    void lasSondasEstanRegistradas() {
        Set<String> controladores = new java.util.HashSet<>();
        VerificadorDeEndpoints.registrados(ctx).forEach(e -> controladores.add(e.metodo().getBeanType().getSimpleName()));
        assertThat(controladores).contains("ConGetExchange", "ConHttpExchange", "ImplementaInterfazHttpExchange",
                "MetodoFinal", "MetodoStatic", "SinControl", "ControladorCorrecto", "ConGetExchangeYRol");
    }

    @Test
    @DisplayName("@GetExchange sin @PreAuthorize: el recorrido lo marca")
    void getExchangeSinRolEsDetectado() throws Exception {
        assertThat(violaciones("ConGetExchange")).isNotEmpty();
    }

    @Test
    @DisplayName("@HttpExchange sin @PreAuthorize: el recorrido lo marca")
    void httpExchangeSinRolEsDetectado() throws Exception {
        assertThat(violaciones("ConHttpExchange")).isNotEmpty();
    }

    @Test
    @DisplayName("interfaz @HttpExchange implementada por un @RestController: el recorrido la marca")
    void interfazDeExchangeEsDetectada() throws Exception {
        assertThat(violaciones("ImplementaInterfazHttpExchange")).isNotEmpty();
    }

    @Test
    @DisplayName("método final con @PreAuthorize válido: el recorrido lo marca (el proxy no lo intercepta)")
    void metodoFinalEsDetectado() throws Exception {
        assertThat(violaciones("MetodoFinal")).isNotEmpty().anyMatch(s -> s.contains("final"));
    }

    @Test
    @DisplayName("método static con @PreAuthorize válido: el recorrido lo marca (el proxy no lo intercepta)")
    void metodoStaticEsDetectado() throws Exception {
        assertThat(violaciones("MetodoStatic")).isNotEmpty().anyMatch(s -> s.contains("static"));
    }

    @Test
    @DisplayName("endpoint sin ningún control: el recorrido lo marca")
    void endpointSinControlEsDetectado() throws Exception {
        assertThat(violaciones("SinControl")).isNotEmpty();
    }

    @Test
    @DisplayName("control positivo: un controlador correcto y un @GetExchange con rol no se marcan")
    void losControladoresCorrectosNoSeMarcan() throws Exception {
        assertThat(violaciones("ControladorCorrecto")).isEmpty();
        assertThat(violaciones("ConGetExchangeYRol")).isEmpty();
    }

    @Test
    @DisplayName("una ruta pública explícita queda fuera del recorrido (esPublico), el resto no")
    void soloLasRutasListadasSonPublicas() {
        Optional<Registrado> sinControl = VerificadorDeEndpoints.registrados(ctx).stream()
                .filter(e -> e.metodo().getBeanType().getSimpleName().equals("SinControl")).findFirst();
        assertThat(sinControl).isPresent();
        assertThat(sinControl.get().esPublico(Set.of())).isFalse();
        assertThat(sinControl.get().esPublico(Set.of("GET /api/publica/sonda/sin-control"))).isTrue();
        assertThat(sinControl.get().esPublico(Set.of("POST /api/publica/sonda/sin-control"))).isFalse();
    }
}
