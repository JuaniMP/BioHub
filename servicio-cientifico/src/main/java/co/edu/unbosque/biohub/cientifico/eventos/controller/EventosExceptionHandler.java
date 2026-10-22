package co.edu.unbosque.biohub.cientifico.eventos.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import co.edu.unbosque.biohub.cientifico.eventos.service.EventoNoPublicadoException;

/** Errores del módulo de eventos como application/problem+json, sin detalles internos. */
@RestControllerAdvice(assignableTypes = EventosPruebaController.class)
public class EventosExceptionHandler {

    @ExceptionHandler(EventoNoPublicadoException.class)
    ResponseEntity<ProblemDetail> brokerNoDisponible() {
        return problema(HttpStatus.SERVICE_UNAVAILABLE, "Servicio de mensajería no disponible. Intente más tarde.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validacion(MethodArgumentNotValidException e) {
        String detalle = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getDefaultMessage())
                .findFirst().orElse("Datos inválidos");
        return problema(HttpStatus.BAD_REQUEST, detalle);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> jsonInvalido() {
        return problema(HttpStatus.BAD_REQUEST, "Cuerpo de la petición inválido");
    }

    private static ResponseEntity<ProblemDetail> problema(HttpStatus estado, String detalle) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(estado, detalle);
        return ResponseEntity.status(estado).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(pd);
    }
}
