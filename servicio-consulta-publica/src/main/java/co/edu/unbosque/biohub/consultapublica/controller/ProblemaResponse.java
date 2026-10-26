package co.edu.unbosque.biohub.consultapublica.controller;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Error estándar {@code application/problem+json} (contrato 04, §2). */
public record ProblemaResponse(String type, String title, int status, String detail, String instance,
                               String codigo, @JsonInclude(JsonInclude.Include.NON_NULL) List<ErrorCampo> errores) {

    public record ErrorCampo(String campo, String mensaje) {
    }
}
