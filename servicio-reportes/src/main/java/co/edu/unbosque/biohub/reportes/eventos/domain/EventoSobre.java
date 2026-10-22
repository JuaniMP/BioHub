package co.edu.unbosque.biohub.reportes.eventos.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre común de todos los eventos (mismo formato que publica servicio-cientifico).
 * Routing key = {@code tipo}.
 */
public record EventoSobre(UUID id, String tipo, String origen, Instant fecha, String usuario,
                          Map<String, Object> datos) {
}
