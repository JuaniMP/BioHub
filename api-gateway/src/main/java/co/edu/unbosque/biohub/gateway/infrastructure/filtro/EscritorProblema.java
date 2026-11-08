package co.edu.unbosque.biohub.gateway.infrastructure.filtro;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.gateway.domain.ResultadoLimite;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Escribe los errores que salen de los filtros, antes de llegar a un controlador. Usa el formato
 * problem+json de 04-contrato-api.md §2: la web decide por {@code status} y {@code codigo}.
 */
@Component
public class EscritorProblema {

    private final ObjectMapper json;

    public EscritorProblema(ObjectMapper json) {
        this.json = json;
    }

    public void limiteExcedido(HttpServletRequest peticion, HttpServletResponse respuesta, ResultadoLimite limite)
            throws IOException {
        respuesta.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(limite.segundosParaReintentar()));
        escribir(peticion, respuesta, HttpStatus.TOO_MANY_REQUESTS, "Demasiadas peticiones",
                "Superaste el límite de peticiones. Intenta de nuevo en unos segundos.", "LIMITE_EXCEDIDO");
    }

    /** Mismo mensaje para key ausente, desconocida, revocada o vencida: no revela cuál de los casos es. */
    public void apiKeyInvalida(HttpServletRequest peticion, HttpServletResponse respuesta) throws IOException {
        respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey");
        escribir(peticion, respuesta, HttpStatus.UNAUTHORIZED, "API Key inválida",
                "Envía una API Key válida en la cabecera X-API-Key.", "API_KEY_INVALIDA");
    }

    public void servicioNoDisponible(HttpServletRequest peticion, HttpServletResponse respuesta) throws IOException {
        escribir(peticion, respuesta, HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "El servicio no está disponible en este momento. Intenta de nuevo.", "SERVICIO_NO_DISPONIBLE");
    }

    public void rolInsuficiente(HttpServletRequest peticion, HttpServletResponse respuesta) throws IOException {
        escribir(peticion, respuesta, HttpStatus.FORBIDDEN, "Acceso denegado",
                "No tienes permiso para ver esta información.", "ROL_INSUFICIENTE");
    }

    private void escribir(HttpServletRequest peticion, HttpServletResponse respuesta, HttpStatus estado, String titulo,
                          String detalle, String codigo) throws IOException {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("type", "about:blank");
        cuerpo.put("title", titulo);
        cuerpo.put("status", estado.value());
        cuerpo.put("detail", detalle);
        cuerpo.put("instance", peticion.getRequestURI());
        cuerpo.put("codigo", codigo);

        respuesta.setStatus(estado.value());
        respuesta.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(respuesta.getOutputStream(), cuerpo);
    }
}
