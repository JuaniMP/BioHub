package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import co.edu.unbosque.biohub.cientifico.registro.domain.VersionIncompletaException;
import co.edu.unbosque.biohub.cientifico.registro.service.DatosHallazgoInvalidosException;
import co.edu.unbosque.biohub.cientifico.registro.service.HallazgoNoEncontradoException;
import co.edu.unbosque.biohub.cientifico.registro.service.PlantillaNoEncontradaException;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.UbicacionInvalidaException;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.UbicacionNoEncontradaException;

/**
 * Traduce las excepciones del módulo registro a {@code application/problem+json}.
 *
 * <p>Incluye las que llegan desde ubicación: cuando ubicación rechaza los datos, la excepción viaja
 * por el evento de dominio síncrono, revierte la transacción y llega hasta aquí. Para quien llama, un
 * dato de ubicación inválido es un 400 igual que cualquier otro campo del formulario, con el nombre
 * del campo que falló (04-contrato-api §6, obligación 1).
 *
 * <p>Los mensajes de estas excepciones están pensados para mostrarse: ninguno incluye coordenadas.
 */
@RestControllerAdvice(basePackages = "co.edu.unbosque.biohub.cientifico.registro")
public class RegistroExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RegistroExceptionHandler.class);
    private static final String TIPO_VALIDACION = "https://biohub/errores/validacion";
    private static final String TIPO_NO_ENCONTRADO = "https://biohub/errores/no-encontrado";

    /** Datos del formulario que no cumplen las reglas de registro (HU-01, HU-03). */
    @ExceptionHandler(DatosHallazgoInvalidosException.class)
    public ResponseEntity<ProblemaRegistroResponse> datosInvalidos(DatosHallazgoInvalidosException e,
                                                                   WebRequest peticion) {
        return validacion(e.campo(), e.getMessage(), "DATOS_INVALIDOS", peticion);
    }

    /** El Builder del historial recibió una versión incompleta o sin justificación (HU-03, HU-05). */
    @ExceptionHandler(VersionIncompletaException.class)
    public ResponseEntity<ProblemaRegistroResponse> versionIncompleta(VersionIncompletaException e,
                                                                      WebRequest peticion) {
        return validacion(e.campo(), e.getMessage(), "VERSION_INCOMPLETA", peticion);
    }

    /** Ubicación rechazó los datos del hallazgo; la transacción ya se revirtió por completo. */
    @ExceptionHandler(UbicacionInvalidaException.class)
    public ResponseEntity<ProblemaRegistroResponse> ubicacionInvalida(UbicacionInvalidaException e,
                                                                      WebRequest peticion) {
        return validacion(e.campo(), e.getMessage(), "UBICACION_INVALIDA", peticion);
    }

    @ExceptionHandler(HallazgoNoEncontradoException.class)
    public ResponseEntity<ProblemaRegistroResponse> hallazgoNoEncontrado(HallazgoNoEncontradoException e,
                                                                         WebRequest peticion) {
        return noEncontrado(e.getMessage(), "HALLAZGO_NO_ENCONTRADO", peticion);
    }

    @ExceptionHandler(PlantillaNoEncontradaException.class)
    public ResponseEntity<ProblemaRegistroResponse> plantillaNoEncontrada(PlantillaNoEncontradaException e,
                                                                          WebRequest peticion) {
        return noEncontrado(e.getMessage(), "PLANTILLA_NO_ENCONTRADA", peticion);
    }

    /**
     * El hallazgo no tenía ubicación registrada antes. Es una inconsistencia entre módulos, no un error
     * del usuario: se responde 409 y queda en el log para revisarlo.
     */
    @ExceptionHandler(UbicacionNoEncontradaException.class)
    public ResponseEntity<ProblemaRegistroResponse> ubicacionNoEncontrada(UbicacionNoEncontradaException e,
                                                                          WebRequest peticion) {
        log.warn("El hallazgo {} no tiene ubicacion registrada", e.hallazgoId());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemaRegistroResponse(TIPO_NO_ENCONTRADO, "Conflicto", HttpStatus.CONFLICT.value(),
                        "El hallazgo no tiene ubicacion registrada", ruta(peticion), "UBICACION_NO_REGISTRADA", null));
    }

    private static ResponseEntity<ProblemaRegistroResponse> validacion(String campo, String mensaje, String codigo,
                                                                       WebRequest peticion) {
        return ResponseEntity.badRequest()
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemaRegistroResponse(TIPO_VALIDACION, "Datos invalidos",
                        HttpStatus.BAD_REQUEST.value(), mensaje, ruta(peticion), codigo,
                        List.of(new ErrorCampoRegistro(campo, mensaje))));
    }

    private static ResponseEntity<ProblemaRegistroResponse> noEncontrado(String mensaje, String codigo,
                                                                         WebRequest peticion) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(new ProblemaRegistroResponse(TIPO_NO_ENCONTRADO, "No encontrado",
                        HttpStatus.NOT_FOUND.value(), mensaje, ruta(peticion), codigo, null));
    }

    private static String ruta(WebRequest peticion) {
        return peticion.getDescription(false).replaceFirst("^uri=", "");
    }
}
