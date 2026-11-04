package co.edu.unbosque.biohub.cientifico.ubicacion.service.vista;

import java.time.LocalDate;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Fila de la búsqueda interna. {@code latitud}/{@code longitud} no aparecen si no se pueden ver o no existen. */
public record HallazgoUbicacionResponse(
        UUID id,
        String nombreCientifico,
        LocalDate fechaRecoleccion,
        String investigador,
        String departamento,
        String departamentoCodigo,
        String region,
        String estadoConservacion,
        String estadoCoordenadas,
        boolean ubicacionProtegida,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double latitud,
        @JsonInclude(JsonInclude.Include.NON_NULL) Double longitud) {

    @Override
    public String toString() {
        return "HallazgoUbicacionResponse[id=" + id + ", estadoCoordenadas=" + estadoCoordenadas + ", coordenadas=***]";
    }
}
