package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import co.edu.unbosque.biohub.cientifico.ubicacion.service.ErrorCampo;

/** application/problem+json (D29): RFC 9457 con las extensiones {@code codigo} y {@code errores[]}. */
public record ProblemaResponse(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String codigo,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<ErrorCampo> errores) {
}
