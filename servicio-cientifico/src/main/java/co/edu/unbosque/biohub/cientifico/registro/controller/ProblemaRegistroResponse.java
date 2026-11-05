package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * {@code application/problem+json} (RFC 9457) con las extensiones {@code codigo} y {@code errores[]},
 * con el mismo formato que usa el módulo ubicación (D29) para que la API del servicio sea homogénea.
 *
 * <p>Es una copia deliberada y no una clase compartida: registro no importa nada de
 * {@code ubicacion.controller} (regla de módulos, SAD §7.1).
 */
public record ProblemaRegistroResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String codigo,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ErrorCampoRegistro> errores) {
}
