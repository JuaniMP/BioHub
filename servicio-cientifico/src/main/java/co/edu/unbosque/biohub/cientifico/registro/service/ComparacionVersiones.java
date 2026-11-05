package co.edu.unbosque.biohub.cientifico.registro.service;

import java.util.List;
import java.util.UUID;

/**
 * Resultado de comparar dos versiones de un hallazgo (HU-05, criterio 3): solo los campos que
 * cambiaron, con su justificación y su autor.
 *
 * @param diferencias vacío si ambas versiones son idénticas en los campos comparados
 */
public record ComparacionVersiones(
        UUID hallazgoId,
        int versionAnterior,
        int versionNueva,
        String justificacion,
        String autor,
        List<DiferenciaCampo> diferencias) {
}
