package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.time.Instant;

import co.edu.unbosque.biohub.cientifico.registro.domain.CambioAuditoria;

/** Un cambio concreto sobre un campo, tal como lo exige el criterio 1 de HU-05. */
public record CambioAuditoriaResponse(
        Integer versionPrevia,
        int versionNueva,
        String campo,
        String valorPrevio,
        String valorNuevo,
        String justificacion,
        String autor,
        Instant fecha) {

    public static CambioAuditoriaResponse de(CambioAuditoria c) {
        return new CambioAuditoriaResponse(c.getVersionPrevia(), c.getVersionNueva(), c.getCampo(),
                c.getValorPrevio(), c.getValorNuevo(), c.getJustificacion(), c.getAutor(), c.getCreadoEn());
    }
}
