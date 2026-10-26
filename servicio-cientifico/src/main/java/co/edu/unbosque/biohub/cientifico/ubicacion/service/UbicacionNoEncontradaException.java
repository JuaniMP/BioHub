package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.util.UUID;

/** El hallazgo no se registró antes con {@link UbicacionService#registrar}. */
public class UbicacionNoEncontradaException extends RuntimeException {

    private final UUID hallazgoId;

    public UbicacionNoEncontradaException(UUID hallazgoId) {
        super("Ubicación no registrada");
        this.hallazgoId = hallazgoId;
    }

    public UUID hallazgoId() {
        return hallazgoId;
    }
}
