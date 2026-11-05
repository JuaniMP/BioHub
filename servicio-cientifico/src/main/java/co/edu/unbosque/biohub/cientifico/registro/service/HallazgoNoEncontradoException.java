package co.edu.unbosque.biohub.cientifico.registro.service;

import java.util.UUID;

/** No existe un hallazgo vigente con ese identificador. */
public class HallazgoNoEncontradoException extends RuntimeException {

    private final transient UUID hallazgoId;

    public HallazgoNoEncontradoException(UUID hallazgoId) {
        super("Hallazgo no encontrado");
        this.hallazgoId = hallazgoId;
    }

    public UUID hallazgoId() {
        return hallazgoId;
    }
}
