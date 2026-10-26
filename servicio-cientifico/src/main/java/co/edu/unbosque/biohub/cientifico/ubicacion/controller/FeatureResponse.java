package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import java.time.LocalDate;
import java.util.UUID;

/** Feature GeoJSON de un hallazgo; {@code coordinates} = [longitud, latitud]. */
public record FeatureResponse(String type, UUID id, Geometria geometry, Propiedades properties) {

    public static FeatureResponse punto(UUID id, double longitud, double latitud, Propiedades propiedades) {
        return new FeatureResponse("Feature", id, new Geometria("Point", new double[] {longitud, latitud}), propiedades);
    }

    public record Geometria(String type, double[] coordinates) {
        @Override
        public String toString() {
            return "Geometria[coordinates=***]";
        }
    }

    public record Propiedades(
            UUID hallazgoId,
            String nombreCientifico,
            LocalDate fechaRecoleccion,
            String investigador,
            String departamento,
            String departamentoCodigo,
            String region,
            String estadoConservacion) {
    }

    @Override
    public String toString() {
        return "FeatureResponse[id=" + id + ", geometry=***]";
    }
}
