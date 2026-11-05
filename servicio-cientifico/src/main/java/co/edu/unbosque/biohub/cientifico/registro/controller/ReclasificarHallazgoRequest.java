package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import co.edu.unbosque.biohub.cientifico.registro.domain.CategoriaUicn;

/**
 * Cuerpo de {@code PUT /api/cientifico/hallazgos/{id}} (HU-03). Los campos nulos no se tocan; la
 * {@code justificacion} científica es obligatoria.
 */
public record ReclasificarHallazgoRequest(
        String nombreCientifico,
        String nombreComun,
        String familia,
        String tipoEspecimen,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate fechaRecoleccion,
        String departamento,
        CategoriaUicn categoriaUicn,
        String descripcion,
        Map<String, Object> atributos,
        String justificacion) {
}
