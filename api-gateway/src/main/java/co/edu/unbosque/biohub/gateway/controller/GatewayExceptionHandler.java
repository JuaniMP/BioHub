package co.edu.unbosque.biohub.gateway.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import co.edu.unbosque.biohub.gateway.service.CircuitoAbiertoException;
import co.edu.unbosque.biohub.gateway.service.ConflictoException;
import co.edu.unbosque.biohub.gateway.service.CuerpoDemasiadoGrandeException;
import co.edu.unbosque.biohub.gateway.service.DatosRechazadosException;
import co.edu.unbosque.biohub.gateway.service.IdentidadNoDisponibleException;
import co.edu.unbosque.biohub.gateway.service.RecursoNoEncontradoException;
import co.edu.unbosque.biohub.gateway.service.RutaNoEncontradaException;

/** Errores del gateway: respuestas genéricas, sin filtrar detalles internos. */
@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail servicioNoDisponible(ResourceAccessException ex) {
        log.warn("Servicio destino no disponible: {}", ex.getMessage());
        return problema(HttpStatus.BAD_GATEWAY, "Servicio no disponible",
                "El servicio solicitado no está disponible en este momento.");
    }

    /** Circuit breaker abierto (ARQ-20): el servicio no responde y no se intenta de nuevo todavía. */
    @ExceptionHandler(CircuitoAbiertoException.class)
    public ResponseEntity<ProblemDetail> circuitoAbierto(CircuitoAbiertoException ex) {
        ProblemDetail problema = conCodigo(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "El servicio solicitado no está disponible en este momento. Intenta de nuevo.", "SERVICIO_NO_DISPONIBLE");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.segundosParaReintentar()))
                .body(problema);
    }

    @ExceptionHandler(CuerpoDemasiadoGrandeException.class)
    public ProblemDetail cuerpoDemasiadoGrande(CuerpoDemasiadoGrandeException ex) {
        return problema(HttpStatus.PAYLOAD_TOO_LARGE, "Petición demasiado grande",
                "El cuerpo de la petición supera el tamaño máximo permitido.");
    }

    @ExceptionHandler(RutaNoEncontradaException.class)
    public ProblemDetail rutaNoEncontrada(RutaNoEncontradaException ex) {
        return problema(HttpStatus.NOT_FOUND, "Ruta no encontrada", "No existe ningún servicio para esta ruta.");
    }

    /** URI o query string mal codificados (p. ej. ?a=%zz). */
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail peticionInvalida(IllegalArgumentException ex) {
        log.debug("Petición inválida: {}", ex.getMessage());
        return problema(HttpStatus.BAD_REQUEST, "Petición inválida", "La URL de la petición no es válida.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail cuerpoInvalido(MethodArgumentNotValidException ex) {
        List<Map<String, String>> errores = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> Map.of("campo", error.getField(), "mensaje", String.valueOf(error.getDefaultMessage())))
                .toList();
        ProblemDetail problema = parametroInvalido("Revisa los datos enviados.");
        problema.setProperty("errores", errores);
        return problema;
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail parametrosInvalidos(HandlerMethodValidationException ex) {
        return parametroInvalido("Revisa los parámetros de la consulta.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail jsonInvalido(HttpMessageNotReadableException ex) {
        return parametroInvalido("El cuerpo de la petición no es válido.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tipoInvalido(MethodArgumentTypeMismatchException ex) {
        return parametroInvalido("Revisa los parámetros de la consulta.");
    }

    @ExceptionHandler(DatosRechazadosException.class)
    public ProblemDetail datosRechazados(DatosRechazadosException ex) {
        return parametroInvalido(ex.getMessage());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ProblemDetail noEncontrado(RecursoNoEncontradoException ex) {
        return conCodigo(HttpStatus.NOT_FOUND, "No encontrado", ex.getMessage(), "NO_ENCONTRADO");
    }

    @ExceptionHandler(ConflictoException.class)
    public ProblemDetail conflicto(ConflictoException ex) {
        return conCodigo(HttpStatus.CONFLICT, "Conflicto", ex.getMessage(), "CONFLICTO");
    }

    @ExceptionHandler(IdentidadNoDisponibleException.class)
    public ProblemDetail identidadNoDisponible(IdentidadNoDisponibleException ex) {
        log.warn("Keycloak no disponible para administrar usuarios: {}", ex.getMessage());
        return conCodigo(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "La administración de usuarios no está disponible en este momento.", "SERVICIO_NO_DISPONIBLE");
    }

    private static ProblemDetail parametroInvalido(String detalle) {
        return conCodigo(HttpStatus.BAD_REQUEST, "Parámetros inválidos", detalle, "PARAMETRO_INVALIDO");
    }

    private static ProblemDetail conCodigo(HttpStatus estado, String titulo, String detalle, String codigo) {
        ProblemDetail problema = problema(estado, titulo, detalle);
        problema.setProperty("codigo", codigo);
        return problema;
    }

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setTitle(titulo);
        problema.setDetail(detalle);
        return problema;
    }
}
