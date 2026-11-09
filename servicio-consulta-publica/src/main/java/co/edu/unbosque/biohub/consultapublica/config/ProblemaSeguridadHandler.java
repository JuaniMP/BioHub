package co.edu.unbosque.biohub.consultapublica.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Errores de seguridad de todo el servicio (04-contrato-api.md §2 y §5.1, SEC-J-20, OBS-J17-1). Un solo punto
 * para los dos casos que resuelve Spring Security:
 * <ul>
 *   <li>{@link #handle}: 403 {@code application/problem+json} con {@code codigo = ROL_INSUFICIENTE}. Lo alcanzan
 *       tanto el {@code denyAll} de la cadena de filtros como cualquier {@code AccessDeniedException} de un
 *       {@code @PreAuthorize} que ningún {@code @RestControllerAdvice} haya resuelto antes (en servicio-cientifico,
 *       el de {@code ubicacion} responde y audita primero, sin cambios).</li>
 *   <li>{@link #commence}: 401 sin credenciales o con JWT inválido. Sigue el contrato (§2: "sin cuerpo,
 *       {@code WWW-Authenticate: Bearer}"): sin cuerpo, con {@code Bearer} (más {@code error="invalid_token"} si el JWT
 *       es inválido, sin la descripción interna del decodificador) y {@code Cache-Control: no-store}. Si el contrato
 *       pasa a exigir problem+json en el 401, basta con escribir el cuerpo aquí (y actualizar las pruebas que hoy
 *       aseguran el cuerpo vacío en {@code ubicacion}).</li>
 * </ul>
 * El cuerpo del 403 es fijo: no incluye el mensaje de la excepción, el rol exigido ni ningún dato del usuario;
 * {@code instance} es solo la ruta pedida. Siempre con {@code Cache-Control: no-store}.
 */
public class ProblemaSeguridadHandler implements AccessDeniedHandler, AuthenticationEntryPoint {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Códigos de error de RFC 6750 que Spring Security emite; cualquier otro valor no se refleja en la cabecera. */
    private static final Pattern CODIGO_BEARER = Pattern.compile("invalid_request|invalid_token|insufficient_scope");

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException e)
            throws IOException {
        escribir(peticion, respuesta, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permiso para ver esta información.", "ROL_INSUFICIENTE");
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException e)
            throws IOException {
        // Solo el código de error de RFC 6750: la descripción de Spring Security trae el mensaje del decodificador
        // del JWT (validador, claim o librería) y no debe llegar al cliente.
        String desafio = "Bearer";
        if (e instanceof OAuth2AuthenticationException oauth
                && CODIGO_BEARER.matcher(String.valueOf(oauth.getError().getErrorCode())).matches()) {
            desafio += " error=\"" + oauth.getError().getErrorCode() + "\"";
        }
        respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
        respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, desafio);
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
    }

    private static void escribir(HttpServletRequest peticion, HttpServletResponse respuesta, HttpStatus estado,
                                 String titulo, String detalle, String codigo) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", "about:blank");
        cuerpo.put("title", titulo);
        cuerpo.put("status", estado.value());
        cuerpo.put("detail", detalle);
        cuerpo.put("instance", peticion.getRequestURI());
        cuerpo.put("codigo", codigo);
        respuesta.setStatus(estado.value());
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        JSON.writeValue(respuesta.getOutputStream(), cuerpo);
    }
}
