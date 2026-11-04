package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.TransactionException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.edu.unbosque.biohub.cientifico.ubicacion.service.AuditoriaAccesoUbicacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.ErrorCampo;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.FuncionNoSoportadaException;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.ParametroInvalidoException;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.RecifradoEnCursoException;
import jakarta.servlet.http.HttpServletRequest;

/** Errores del módulo en application/problem+json (D29), sin trazas ni detalles internos. */
@RestControllerAdvice(basePackages = "co.edu.unbosque.biohub.cientifico.ubicacion.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class UbicacionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(UbicacionExceptionHandler.class);

    // ObjectProvider: en los @WebMvcTest de otros módulos este advice se carga sin los servicios de ubicación
    private final ObjectProvider<AuditoriaAccesoUbicacion> auditoria;

    public UbicacionExceptionHandler(ObjectProvider<AuditoriaAccesoUbicacion> auditoria) {
        this.auditoria = auditoria;
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    ResponseEntity<ProblemaResponse> parametroInvalido(ParametroInvalidoException e, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, "Parámetros inválidos", "Revisa los parámetros de la consulta.",
                "PARAMETRO_INVALIDO", req, e.errores());
    }

    @ExceptionHandler(FuncionNoSoportadaException.class)
    ResponseEntity<ProblemaResponse> noSoportado(FuncionNoSoportadaException e, HttpServletRequest req) {
        return problema(HttpStatus.BAD_REQUEST, "Función no soportada", e.getMessage(), "NO_SOPORTADO", req, null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemaResponse> accesoDenegado(AccessDeniedException e, HttpServletRequest req) throws AccessDeniedException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            throw e; // lo resuelve Spring Security (401 sin cuerpo)
        }
        // Los 403 se auditan aquí (el servicio ya auditó el caso de bbox). SEC-J-08: también el de POST /recifrado,
        // un intento de un no administrador sobre la operación criptográfica es justo lo que conviene ver
        auditoria.ifAvailable(a -> a.registrar(auth.getName(), req.getMethod() + " " + req.getRequestURI(),
                false, "", 0, 0));
        return problema(HttpStatus.FORBIDDEN, "Acceso denegado", "No tienes permiso para ver esta información.",
                "ROL_INSUFICIENTE", req, null);
    }

    @ExceptionHandler(RecifradoEnCursoException.class)
    ResponseEntity<ProblemaResponse> conflicto(RecifradoEnCursoException e, HttpServletRequest req) {
        return problema(HttpStatus.CONFLICT, "Conflicto", "Ya hay un re-cifrado en curso.", "CONFLICTO", req, null);
    }

    @ExceptionHandler({DataAccessException.class, TransactionException.class})
    ResponseEntity<ProblemaResponse> bdNoDisponible(Exception e, HttpServletRequest req) {
        log.error("Base de datos no disponible o error de acceso a datos: {}", e.getClass().getSimpleName());
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio no disponible",
                "El servicio no está disponible. Inténtalo más tarde.", "SERVICIO_NO_DISPONIBLE", req, null);
    }

    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<ProblemaResponse> inesperado(RuntimeException e, HttpServletRequest req) {
        if (e instanceof ErrorResponse) {
            throw e; // errores estándar de Spring MVC: los resuelve el manejador por defecto
        }
        log.error("Error no previsto en ubicación", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Ocurrió un error inesperado.",
                "ERROR_INTERNO", req, null);
    }

    private static ResponseEntity<ProblemaResponse> problema(HttpStatus estado, String titulo, String detalle,
                                                             String codigo, HttpServletRequest req,
                                                             List<ErrorCampo> errores) {
        return ResponseEntity.status(estado)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .cacheControl(CacheControl.noStore())
                .body(new ProblemaResponse("about:blank", titulo, estado.value(), detalle, req.getRequestURI(),
                        codigo, errores));
    }
}
