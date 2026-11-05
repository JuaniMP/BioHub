package co.edu.unbosque.biohub.cientifico.registro.controller;

/**
 * Un campo que no pasó la validación, con un mensaje apto para mostrarle al usuario.
 *
 * @param campo   nombre del campo del request (p. ej. {@code fechaRecoleccion})
 * @param mensaje explicación corta; nunca incluye coordenadas ni valores sensibles
 */
public record ErrorCampoRegistro(String campo, String mensaje) {
}
