package co.edu.unbosque.biohub.cientifico.eventos.controller;

import jakarta.validation.constraints.Size;

/** Cuerpo opcional del evento de prueba. */
public record EventoPruebaRequest(@Size(max = 200, message = "mensaje: máximo 200 caracteres") String mensaje) {
}
