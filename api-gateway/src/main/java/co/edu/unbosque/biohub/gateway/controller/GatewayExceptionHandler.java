package co.edu.unbosque.biohub.gateway.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;

import co.edu.unbosque.biohub.gateway.service.CuerpoDemasiadoGrandeException;
import co.edu.unbosque.biohub.gateway.service.RutaNoEncontradaException;

/** Errores del proxy: respuestas genéricas, sin filtrar detalles internos. */
@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    @ExceptionHandler(ResourceAccessException.class)
    public ProblemDetail servicioNoDisponible(ResourceAccessException ex) {
        log.warn("Servicio destino no disponible: {}", ex.getMessage());
        return problema(HttpStatus.BAD_GATEWAY, "Servicio no disponible",
                "El servicio solicitado no está disponible en este momento.");
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

    private static ProblemDetail problema(HttpStatus estado, String titulo, String detalle) {
        ProblemDetail problema = ProblemDetail.forStatus(estado);
        problema.setTitle(titulo);
        problema.setDetail(detalle);
        return problema;
    }
}
