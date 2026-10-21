package co.edu.unbosque.biohub.gateway.service;

/** Ningún servicio atiende el prefijo /api/&lt;prefijo&gt;/ solicitado. */
public class RutaNoEncontradaException extends RuntimeException {

    public RutaNoEncontradaException() {
        super("Ruta no encontrada");
    }
}
