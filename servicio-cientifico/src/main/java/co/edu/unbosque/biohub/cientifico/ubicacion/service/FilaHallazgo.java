package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.time.LocalDate;
import java.util.UUID;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;

/** Fila leída de ubicacion_hallazgo + catálogo. {@code cifradas} solo viene si la consulta lo pidió. */
record FilaHallazgo(
        UUID id,
        String nombreCientifico,
        LocalDate fechaRecoleccion,
        String investigador,
        EstadoConservacion estado,
        String departamento,
        String departamentoCodigo,
        String region,
        boolean tieneCoordenadas,
        CoordenadasCifradas cifradas) {

    @Override
    public String toString() {
        return "FilaHallazgo[id=" + id + ", coordenadas=***]";
    }
}
