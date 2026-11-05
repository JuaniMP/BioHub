package co.edu.unbosque.biohub.cientifico.registro.service;

import java.util.UUID;

/**
 * Resultado de registrar o reclasificar un hallazgo.
 *
 * @param hallazgoId identificador del hallazgo
 * @param version    versión resultante; la misma que viaja a ubicación y al evento público
 */
public record ResultadoRegistro(UUID hallazgoId, int version) {
}
