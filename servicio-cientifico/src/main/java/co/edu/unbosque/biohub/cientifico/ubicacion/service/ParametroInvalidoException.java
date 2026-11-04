package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.util.List;

/** Parámetros de consulta inválidos (400 PARAMETRO_INVALIDO). */
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
