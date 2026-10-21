package co.edu.unbosque.biohub.cientifico.controller;

import java.time.Instant;
import java.util.List;

/** Respuesta de la prueba de punta a punta. */
public record PingResponse(String servicio, String usuario, List<String> roles, Instant fecha) {
}
