package co.edu.unbosque.biohub.cientifico.registro.service;

/**
 * Una diferencia entre dos versiones de un hallazgo (HU-05): el auditor ve el campo, cómo estaba y
 * cómo quedó.
 */
public record DiferenciaCampo(String campo, Object valorAnterior, Object valorNuevo) {
}
