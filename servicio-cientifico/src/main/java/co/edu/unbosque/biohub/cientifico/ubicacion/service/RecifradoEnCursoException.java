package co.edu.unbosque.biohub.cientifico.ubicacion.service;

/** Ya hay un re-cifrado en curso (409 CONFLICTO). */
public class RecifradoEnCursoException extends RuntimeException {

    public RecifradoEnCursoException() {
        super("Ya hay un re-cifrado en curso");
    }
}
