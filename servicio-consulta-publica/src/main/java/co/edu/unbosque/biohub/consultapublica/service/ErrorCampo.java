package co.edu.unbosque.biohub.consultapublica.service;

/** Error de validación de un campo (contrato 04, §2: elemento de {@code errores[]}). */
public record ErrorCampo(String campo, String mensaje) {
}
