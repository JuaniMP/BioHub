package co.edu.unbosque.biohub.consultapublica.controller;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.edu.unbosque.biohub.consultapublica.service.ErrorCampo;
import co.edu.unbosque.biohub.consultapublica.service.FichaNoEncontradaException;
import co.edu.unbosque.biohub.consultapublica.service.ParametroInvalidoException;

/**
 * Errores de los endpoints públicos (D29): {@code application/problem+json} con {@code codigo} estable y
 * {@code errores[]} por campo, siempre con {@code Cache-Control: no-store}. Nunca expone trazas, SQL ni
 * detalles internos. Acotado a {@link FichasPublicasController}: no altera el 401/403 de Spring Security
 * ni los demás controladores.
 */
@RestControllerAdvice(assignableTypes = FichasPublicasController.class)
public class ConsultaPublicaExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ConsultaPublicaExceptionHandler.class);

    @ExceptionHandler(ParametroInvalidoException.class)
    ResponseEntity<ProblemaResponse> parametroInvalido(ParametroInvalidoException e, HttpServletRequest req) {
        return respuesta(HttpStatus.BAD_REQUEST, "Parámetros inválidos",
                "Revisa los parámetros de la consulta.", "PARAMETRO_INVALIDO", req, e.errores());
    }

    @ExceptionHandler(FichaNoEncontradaException.class)
    ResponseEntity<ProblemaResponse> noEncontrada(FichaNoEncontradaException e, HttpServletRequest req) {
        return respuesta(HttpStatus.NOT_FOUND, "No encontrado", "La ficha solicitada no existe.",
                "NO_ENCONTRADO", req, null);
    }

    /** mysql-lectura caída (o sin conexiones) y dato fuera de caché. */
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ProblemaResponse> baseDeDatos(DataAccessException e, HttpServletRequest req) {
        log.error("Base de datos no disponible ({})", e.getClass().getSimpleName());
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "El servicio no está disponible en este momento. Intenta de nuevo.",
                "SERVICIO_NO_DISPONIBLE", req, null);
    }

    /**
     * Un 401/403 de {@code @PreAuthorize} no es un error interno: se devuelve a Spring Security para que lo resuelva
     * {@code ProblemaSeguridadHandler} (problem+json). Sin esto lo capturaría el manejador genérico como 500.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void seguridad(RuntimeException e) {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemaResponse> inesperado(Exception e, HttpServletRequest req) {
        if (e instanceof ErrorResponse mvc && mvc.getStatusCode().is4xxClientError()) {
            // Errores propios de Spring MVC (método no permitido, etc.): se conserva el estado 4xx
            HttpStatus estado = HttpStatus.valueOf(mvc.getStatusCode().value());
            return respuesta(estado, "Solicitud inválida", "La solicitud no es válida.",
                    estado == HttpStatus.NOT_FOUND ? "NO_ENCONTRADO" : "PARAMETRO_INVALIDO", req, null);
        }
        log.error("Error no previsto en consulta pública ({})", e.getClass().getName());
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno",
                "Ocurrió un error inesperado. Intenta de nuevo.", "ERROR_INTERNO", req, null);
    }

    private static ResponseEntity<ProblemaResponse> respuesta(HttpStatus estado, String titulo, String detalle,
                                                              String codigo, HttpServletRequest req,
                                                              List<ErrorCampo> errores) {
        ProblemaResponse cuerpo = new ProblemaResponse("about:blank", titulo, estado.value(), detalle,
                req.getRequestURI(), codigo, errores);
        return ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(cuerpo);
    }
}
