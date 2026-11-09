package co.edu.unbosque.biohub.sondaseguridad;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.context.annotation.Bean;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sondas de SEC-J-19: una clase por cada vía de elusión conocida de la regla de endpoints con rol explícito.
 * Viven fuera del paquete base de la aplicación ({@code ...biohub.cientifico}) a propósito: ni el escaneo de
 * componentes de Spring ni {@code ArquitecturaSeguridadTest} (que además excluye las clases de test) las ven.
 * Solo se evalúan con {@code ClassFileImporter.importClasses(...)} en las pruebas de control de esa clase.
 */
public final class SondasElusion {

    private SondasElusion() {
    }

    /** Estereotipo propio meta-anotado con {@code @RestController}. */
    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @RestController
    public @interface MiRest {
    }

    // ---- controlador correcto: ninguna regla debe fallar ----

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class ControladorCorrecto {
        @GetMapping("/a")
        @PreAuthorize("hasRole('administrador')")
        public String uno() {
            return "x";
        }

        @PostMapping("/b")
        @PreAuthorize("hasAnyRole('investigador_campo', 'cientifico_alto_rango')")
        public String dos() {
            return "x";
        }

        @GetMapping("/c")
        @PreAuthorize("@politicaUbicacion.puedeVerCoordenadas(authentication)")
        public String tres() {
            return "x";
        }
    }

    // ---- vías de elusión ----

    @Controller
    @ResponseBody
    @RequestMapping("/api/cientifico/sonda")
    public static class ControllerConResponseBody {
        @GetMapping("/sin-rol")
        public String sinRol() {
            return "x";
        }
    }

    @Controller
    @RequestMapping("/api/cientifico/sonda")
    public static class ControllerConResponseEntity {
        @GetMapping("/sin-rol")
        public ResponseEntity<String> sinRol() {
            return ResponseEntity.ok("x");
        }
    }

    /** {@code @Controller} con rol correcto: igual se rechaza (solo se admite {@code @RestController}). */
    @Controller
    @RequestMapping("/api/cientifico/sonda")
    public static class ControllerConRolCorrecto {
        @GetMapping("/con-rol")
        @PreAuthorize("hasRole('administrador')")
        public ResponseEntity<String> conRol() {
            return ResponseEntity.ok("x");
        }
    }

    @MiRest
    @RequestMapping("/api/cientifico/sonda")
    public static class ConEstereotipoPropio {
        @GetMapping("/estereotipo")
        @PreAuthorize("hasRole('administrador')")
        public String estereotipo() {
            return "x";
        }
    }

    /** Clase sin estereotipo de controlador pero con {@code @RequestMapping} de tipo: Spring la trata como handler. */
    @Component
    @RequestMapping("/api/cientifico/sonda")
    public static class ComponenteConRequestMapping {
        @GetMapping("/componente")
        @PreAuthorize("hasRole('administrador')")
        public String componente() {
            return "x";
        }
    }

    public abstract static class BaseConMappingSinRol {
        @GetMapping("/heredado")
        public String heredado() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class HeredaMappingDeClaseBase extends BaseConMappingSinRol {
    }

    public abstract static class BaseConMappingYRol {
        @GetMapping("/heredado-con-rol")
        @PreAuthorize("hasRole('administrador')")
        public String heredado() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class HeredaMappingConRol extends BaseConMappingYRol {
    }

    public interface ApiGenerada {
        @GetMapping("/interfaz")
        String interfaz();
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class ImplementaInterfazConMapping implements ApiGenerada {
        @Override
        public String interfaz() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class IsAuthenticated {
        @GetMapping("/auth")
        @PreAuthorize("isAuthenticated()")
        public String auth() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class PermitAllEnCientifico {
        @GetMapping("/abierto")
        @PreAuthorize("permitAll()")
        public String abierto() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class PreAuthorizeVacio {
        @GetMapping("/vacio")
        @PreAuthorize("")
        public String vacio() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class PreAuthorizeTrivial {
        @GetMapping("/trivial")
        @PreAuthorize("true")
        public String trivial() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class RolMasPermitAll {
        @GetMapping("/mixto")
        @PreAuthorize("hasRole('administrador') or permitAll()")
        public String mixto() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class RolMasTautologia {
        @GetMapping("/tautologia")
        @PreAuthorize("hasRole('administrador') or 1 == 1")
        public String tautologia() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class RolInexistente {
        @GetMapping("/rol-raro")
        @PreAuthorize("hasRole('cualquiera')")
        public String rolRaro() {
            return "x";
        }
    }

    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class PoliticaNoListada {
        @GetMapping("/politica")
        @PreAuthorize("@politicaUbicacion.esCualquiera(authentication)")
        public String politica() {
            return "x";
        }
    }

    // ---- SEC-J-22: vías que Spring registra como endpoint y no pasan por el proxy de @PreAuthorize ----

    /** {@code @GetExchange} en un {@code @RestController} (Spring 6.1+): endpoint sin {@code @RequestMapping}. */
    @RestController
    public static class ConGetExchange {
        @GetExchange("/api/cientifico/sonda/exchange")
        public String exchange() {
            return "x";
        }
    }

    /** Lo mismo con rol: la anotación de exchange no debe saltarse la regla de rol explícito (R4). */
    @RestController
    public static class ConGetExchangeYRol {
        @GetExchange("/api/cientifico/sonda/exchange-con-rol")
        @PreAuthorize("hasRole('administrador')")
        public String exchange() {
            return "x";
        }
    }

    /** {@code @HttpExchange} directo en el método. */
    @RestController
    public static class ConHttpExchange {
        @HttpExchange(method = "GET", url = "/api/cientifico/sonda/http-exchange")
        public String exchange() {
            return "x";
        }
    }

    public interface ApiExchange {
        @GetExchange("/api/cientifico/sonda/interfaz-exchange")
        String interfazExchange();
    }

    /** Implementa una interfaz con {@code @GetExchange}: el endpoint se hereda sin {@code @PreAuthorize}. */
    @RestController
    public static class ImplementaInterfazHttpExchange implements ApiExchange {
        @Override
        public String interfazExchange() {
            return "x";
        }
    }

    /** {@code final}: CGLIB no puede sobrescribirlo y el proxy no lo intercepta, aunque lleve un rol válido. */
    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class MetodoFinal {
        @GetMapping("/final")
        @PreAuthorize("hasRole('administrador')")
        public final String metodoFinal() {
            return "x";
        }
    }

    /** {@code static}: tampoco pasa por el proxy. */
    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class MetodoStatic {
        @GetMapping("/static")
        @PreAuthorize("hasRole('administrador')")
        public static String metodoStatic() {
            return "x";
        }
    }

    /** Método de endpoint que no es público. */
    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class MetodoNoPublico {
        @GetMapping("/no-publico")
        @PreAuthorize("hasRole('administrador')")
        String noPublico() {
            return "x";
        }
    }

    /** Clase {@code final}: no se puede proxiar. */
    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static final class ClaseFinal {
        @GetMapping("/clase-final")
        @PreAuthorize("hasRole('administrador')")
        public String claseFinal() {
            return "x";
        }
    }

    /** Control de la prueba de comportamiento: un endpoint sin ningún control de rol. */
    @RestController
    @RequestMapping("/api/cientifico/sonda")
    public static class SinControl {
        @GetMapping("/sin-control")
        public String sinControl() {
            return "x";
        }
    }

    /** Rutas funcionales: no pasan por ningún {@code @PreAuthorize}. */
    public static class RutasFuncionales {
        @Bean
        public RouterFunction<ServerResponse> rutas() {
            return RouterFunctions.route().GET("/api/cientifico/sonda/funcional", r -> ServerResponse.ok().build())
                    .build();
        }
    }

    /** Lo mismo, ocultando el tipo declarado de retorno: igual se detecta por la dependencia de {@code ...function}. */
    public static class RutasFuncionalesOcultas {
        @Bean
        public Object rutas() {
            return RouterFunctions.route().build();
        }
    }

    /** Handler por nombre de bean ({@code BeanNameUrlHandlerMapping}). */
    public static class ManejadorHttpRequestHandler implements HttpRequestHandler {
        @Override
        public void handleRequest(HttpServletRequest request, HttpServletResponse response) {
            // sin control de rol
        }
    }

    /** Servlet propio: queda fuera de Spring MVC y de {@code @PreAuthorize}. */
    public static class ServletPropio extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException {
            // sin control de rol
        }
    }
}
