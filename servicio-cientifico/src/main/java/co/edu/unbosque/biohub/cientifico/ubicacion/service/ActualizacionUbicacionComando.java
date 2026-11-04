package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.time.LocalDate;
import java.util.UUID;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;

/** Reclasificación o edición de los datos indexados. No cambia coordenadas ni investigador. */
public record ActualizacionUbicacionComando(
        UUID hallazgoId,
        int version,
        String nombreCientifico,
        LocalDate fechaRecoleccion,
        String departamento,
        EstadoConservacion estadoConservacion) {
}
