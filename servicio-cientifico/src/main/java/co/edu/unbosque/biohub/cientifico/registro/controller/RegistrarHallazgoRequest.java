package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonFormat;

import co.edu.unbosque.biohub.cientifico.registro.domain.CategoriaUicn;

/**
 * Cuerpo de {@code POST /api/cientifico/hallazgos} (HU-01).
 *
 * <p>Las coordenadas llegan aquí y se entregan a ubicación para que las cifre; este módulo no las
 * guarda. {@link #toString()} las enmascara para que no aparezcan en ningún log de error ni de
 * depuración (04-contrato-api §6, obligación 2).
 */
public record RegistrarHallazgoRequest(
        String nombreCientifico,
        String nombreComun,
        String familia,
        String tipoEspecimen,
        @JsonFormat(pattern = "yyyy-MM-dd") LocalDate fechaRecoleccion,
        String departamento,
        CategoriaUicn categoriaUicn,
        String descripcion,
        Double latitud,
        Double longitud,
        Map<String, Object> atributos) {

    @Override
    public String toString() {
        return "RegistrarHallazgoRequest[nombreCientifico=" + nombreCientifico
                + ", fechaRecoleccion=" + fechaRecoleccion + ", coordenadas=***]";
    }
}
