package co.edu.unbosque.biohub.cientifico.eventos.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Sobre común de todos los eventos. Routing key = {@code tipo}.
 *
 * @param id      identificador único del evento
 * @param tipo    p. ej. "prueba.ping" o "hallazgo.registrado"
 * @param origen  servicio que publica
 * @param fecha   instante de creación (UTC, ISO-8601)
 * @param usuario preferred_username del JWT que originó el evento
 * @param datos   carga útil específica del tipo
 */
public record EventoSobre(UUID id, String tipo, String origen, Instant fecha, String usuario,
                          Map<String, Object> datos) {
}
