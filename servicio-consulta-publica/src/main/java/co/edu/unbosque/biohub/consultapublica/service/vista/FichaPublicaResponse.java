package co.edu.unbosque.biohub.consultapublica.service.vista;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Ficha pública de una especie (contrato 04, §4.2). D14: nunca lleva coordenadas ni datos del investigador;
 * {@code ubicacionProtegida} es siempre {@code true}.
 */
public record FichaPublicaResponse(UUID id, String nombreCientifico, String nombreComun, String familia,
                                   String tipoEspecimen, LocalDate fechaRecoleccion, String estadoConservacion,
                                   String departamento, String departamentoCodigo, String region,
                                   String descripcion, boolean ubicacionProtegida) {

    /** Construye la respuesta fijando {@code ubicacionProtegida = true}. */
    public static FichaPublicaResponse protegida(UUID id, String nombreCientifico, String nombreComun,
                                                 String familia, String tipoEspecimen, LocalDate fechaRecoleccion,
                                                 String estadoConservacion, String departamento,
                                                 String departamentoCodigo, String region, String descripcion) {
        return new FichaPublicaResponse(id, nombreCientifico, nombreComun, familia, tipoEspecimen,
                fechaRecoleccion, estadoConservacion, departamento, departamentoCodigo, region, descripcion, true);
    }
}
