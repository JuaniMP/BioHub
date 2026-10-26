package co.edu.unbosque.biohub.consultapublica.service;

/** La ficha pedida no existe: se traduce a 404 {@code NO_ENCONTRADO}. Las excepciones no se cachean. */
public class FichaNoEncontradaException extends RuntimeException {

    public FichaNoEncontradaException() {
        super("La ficha solicitada no existe.");
    }
}
