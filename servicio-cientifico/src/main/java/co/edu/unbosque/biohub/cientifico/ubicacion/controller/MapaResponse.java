package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import java.util.List;

/** GeoJSON FeatureCollection + miembros propios. */
public record MapaResponse(
        String type,
        List<FeatureResponse> features,
        long total,
        int devueltos,
        boolean truncado,
        int omitidos,
        int limite) {
}
