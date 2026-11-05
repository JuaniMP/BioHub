package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import co.edu.unbosque.biohub.cientifico.registro.domain.VersionHallazgo;

/** Una versión del historial tal como la ve el auditor (HU-05). */
public record VersionResponse(
        UUID id,
        int version,
        Map<String, Object> snapshot,
        String justificacion,
        String autor,
        Instant creadoEn) {

    public static VersionResponse de(VersionHallazgo v) {
        return new VersionResponse(v.getId(), v.getVersion(), v.getSnapshot(), v.getJustificacion(),
                v.getAutor(), v.getCreadoEn());
    }
}
