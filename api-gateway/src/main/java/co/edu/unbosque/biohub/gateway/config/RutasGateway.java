package co.edu.unbosque.biohub.gateway.config;

import java.util.List;

import org.springframework.http.HttpMethod;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Define en un solo lugar qué rutas son portal y cuáles son API externa. Lo usan SecurityConfig y
 * ApiKeyRateLimitFilter: si cada uno comparara rutas a su manera podrían no coincidir (SEC-05).
 */
@Component
public class RutasGateway {

    /** Prefijo público y estable de la API para consumidores externos (D16, ASR9). */
    public static final String PREFIJO_API_EXTERNA = "/api/v1/externa";

    /** Único recurso que se expone a externos: nunca /api/publica/eventos/**. */
    public static final String RECURSO_FICHAS = "/fichas";

    private final RequestMatcher apiExterna;
    private final RequestMatcher portal;
    private final String[] prefijosInternos;

    public RutasGateway(GatewayProperties props) {
        PathPatternRequestMatcher.Builder rutas = PathPatternRequestMatcher.withDefaults();
        this.apiExterna = rutas.matcher(HttpMethod.GET, PREFIJO_API_EXTERNA + RECURSO_FICHAS + "/**");

        List<RequestMatcher> publicas = props.rutasPublicas().stream()
                .map(prefijo -> (RequestMatcher) rutas.matcher(HttpMethod.GET, "/api/" + prefijo + "/**"))
                .toList();
        this.portal = publicas.isEmpty() ? peticion -> false : new OrRequestMatcher(publicas);

        this.prefijosInternos = props.rutas().keySet().stream()
                .filter(prefijo -> !props.rutasPublicas().contains(prefijo))
                .map(prefijo -> "/api/" + prefijo + "/**")
                .toArray(String[]::new);
    }

    public RequestMatcher apiExterna() {
        return apiExterna;
    }

    public RequestMatcher portal() {
        return portal;
    }

    /** Patrones de los servicios internos, los que exigen JWT con rol. */
    public String[] prefijosInternos() {
        return prefijosInternos.clone();
    }

    public boolean esApiExterna(HttpServletRequest peticion) {
        return apiExterna.matches(peticion);
    }

    public boolean esPortal(HttpServletRequest peticion) {
        return portal.matches(peticion);
    }
}
