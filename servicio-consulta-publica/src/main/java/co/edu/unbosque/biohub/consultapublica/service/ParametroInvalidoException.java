package co.edu.unbosque.biohub.consultapublica.service;

import java.util.List;

import co.edu.unbosque.biohub.consultapublica.service.ErrorCampo;

/** Parámetro mal formado o fuera de rango: se traduce a 400 {@code PARAMETRO_INVALIDO}. */
public class ParametroInvalidoException extends RuntimeException {

    private final transient List<ErrorCampo> errores;

    public ParametroInvalidoException(List<ErrorCampo> errores) {
        super("Parámetros inválidos");
        this.errores = List.copyOf(errores);
    }

    public List<ErrorCampo> errores() {
        return errores;
    }
}
