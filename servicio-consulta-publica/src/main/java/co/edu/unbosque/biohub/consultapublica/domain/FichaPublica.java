package co.edu.unbosque.biohub.consultapublica.domain;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Fila del modelo de lectura público. D14: no existe ningún atributo de coordenadas y tampoco se guarda
 * el investigador ni el usuario del evento.
 */
public record FichaPublica(UUID hallazgoId, int version, String nombreCientifico, String nombreComun,
                           String familia, String tipoEspecimen, LocalDate fechaRecoleccion,
                           EstadoConservacion estadoConservacion, String departamentoCodigo,
                           String departamentoNombre, String regionNombre, String descripcion) {
}
