package co.edu.unbosque.biohub.cientifico.registro.service;

import java.time.LocalDate;
import java.util.Map;

import co.edu.unbosque.biohub.cientifico.registro.domain.CategoriaUicn;

/**
 * Datos para reclasificar o corregir un hallazgo ya registrado (HU-03). Los campos nulos se dejan como
 * están; la {@code justificacion} es obligatoria siempre.
 *
 * <p>No incluye coordenadas: corregirlas no está en el contrato con ubicación (04-contrato-api §6,
 * obligación 6). Tampoco cambia el investigador responsable del hallazgo original.
 */
public record ReclasificarHallazgoComando(
        String nombreCientifico,
        String nombreComun,
        String familia,
        String tipoEspecimen,
        LocalDate fechaRecoleccion,
        String departamento,
        CategoriaUicn categoriaUicn,
        String descripcion,
        Map<String, Object> atributos,
        String justificacion) {
}
