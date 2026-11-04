package co.edu.unbosque.biohub.cientifico.eventos.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * Evento de dominio EN PROCESO (patrón Observer del SAD §6.4.2, ARQ-08), distinto de {@link EventoSobre}, que es
 * el mensaje de integración que viaja por RabbitMQ. Se publica con Spring {@code ApplicationEventPublisher} y lo
 * reciben observadores en la misma JVM: {@code ObservadorUbicacion} (síncrono, misma transacción) y, a cargo de
 * registro, un {@code @TransactionalEventListener(AFTER_COMMIT)} que arma el {@link EventoSobre} público.
 *
 * <p>Paquete compartido: el dueño del uso es registro (Hernando); ubicacion solo escucha. Estos tipos no
 * conocen a ningún módulo de negocio.
 */
public interface EventoDominio {

    /** Hallazgo al que se refiere el evento. */
    UUID hallazgoId();

    /** Versión del hallazgo tras el cambio (≥ 1, crece estrictamente por hallazgo; es la misma del evento público). */
    int version();

    /** Instante en que ocurrió, en UTC. */
    Instant ocurridoEn();
}
