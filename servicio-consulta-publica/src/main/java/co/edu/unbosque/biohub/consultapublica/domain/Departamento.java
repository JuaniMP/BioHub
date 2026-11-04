package co.edu.unbosque.biohub.consultapublica.domain;

/** Fila del catálogo de departamentos (código DANE de 2 dígitos, nombre oficial y región). */
public record Departamento(String codigo, String nombre, String region) {
}
