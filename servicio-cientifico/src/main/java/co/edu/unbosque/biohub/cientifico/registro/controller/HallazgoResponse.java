package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import co.edu.unbosque.biohub.cientifico.registro.domain.CategoriaUicn;
import co.edu.unbosque.biohub.cientifico.registro.domain.Hallazgo;

/**
 * Vista de un hallazgo para la aplicación interna. No incluye coordenadas: quien necesite el mapa usa
 * los endpoints de ubicación, que aplican la política de acceso según el rol (RF13).
 */
public record HallazgoResponse(
        UUID id,
        int version,
        String nombreCientifico,
        String nombreComun,
        String familia,
        String tipoEspecimen,
        LocalDate fechaRecoleccion,
        String investigador,
        String departamento,
        String estadoConservacion,
        CategoriaUicn categoriaUicn,
        String descripcion,
        Map<String, Object> atributos,
        boolean anulado) {

    public static HallazgoResponse de(Hallazgo h) {
        return new HallazgoResponse(h.getId(), h.getVersion(), h.getNombreCientifico(), h.getNombreComun(),
                h.getFamilia(), h.getTipoEspecimen(), h.getFechaRecoleccion(), h.getInvestigador(),
                h.getDepartamento(), h.getEstadoConservacion().name(), h.getCategoriaUicn(), h.getDescripcion(),
                h.getAtributos(), h.estaAnulado());
    }
}
