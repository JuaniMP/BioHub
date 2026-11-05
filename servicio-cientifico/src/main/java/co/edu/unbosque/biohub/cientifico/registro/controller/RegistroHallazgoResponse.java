package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.util.UUID;

import co.edu.unbosque.biohub.cientifico.registro.service.ResultadoRegistro;

/** Respuesta de un alta o una reclasificación: el identificador del hallazgo y su versión resultante. */
public record RegistroHallazgoResponse(UUID hallazgoId, int version) {

    public static RegistroHallazgoResponse de(ResultadoRegistro resultado) {
        return new RegistroHallazgoResponse(resultado.hallazgoId(), resultado.version());
    }
}
