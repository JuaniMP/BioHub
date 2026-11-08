package co.edu.unbosque.biohub.gateway.service;

/** Consumidor, key o usuario inexistente (404 NO_ENCONTRADO). */
public class RecursoNoEncontradoException extends RuntimeException {

    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
