package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import java.util.Map;

public record RecifradoResponse(
        String claveActiva,
        int revisados,
        int recifrados,
        int fallidos,
        Map<String, Long> pendientesPorVersion) {
}
