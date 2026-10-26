package co.edu.unbosque.biohub.cientifico.ubicacion.service;

/** Error de validación de un parámetro (se serializa en {@code errores[]} del problem+json). */
public record ErrorCampo(String campo, String mensaje) {
}
