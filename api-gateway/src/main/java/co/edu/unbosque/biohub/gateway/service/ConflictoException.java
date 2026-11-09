package co.edu.unbosque.biohub.gateway.service;

/** La operación choca con el estado actual (409 CONFLICTO). El mensaje se puede mostrar al usuario. */
public class ConflictoException extends RuntimeException {

    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
