package co.edu.unbosque.biohub.cientifico.eventos.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.cientifico.eventos.domain.EventoSobre;

/** Publica eventos persistentes en el exchange topic, esperando la confirmación (publisher confirm) del broker. */
@Service
public class PublicadorEventos {

    static final String ORIGEN = "servicio-cientifico";
    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final Duration timeoutConfirmacion;
    private final Clock reloj;

    public PublicadorEventos(RabbitTemplate rabbitTemplate,
                             @Value("${biohub.eventos.exchange}") String exchange,
                             @Value("${biohub.eventos.timeout-confirmacion:5s}") Duration timeoutConfirmacion,
                             Clock reloj) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.timeoutConfirmacion = timeoutConfirmacion;
        this.reloj = reloj;
    }

    /**
     * Publica un evento con routing key = tipo.
     *
     * @throws EventoNoPublicadoException si el broker no responde o no confirma
     */
    public EventoSobre publicar(String tipo, String usuario, Map<String, Object> datos) {
        EventoSobre evento = new EventoSobre(UUID.randomUUID(), tipo, ORIGEN, Instant.now(reloj), usuario, datos);
        try {
            rabbitTemplate.invoke(operaciones -> {
                operaciones.convertAndSend(exchange, tipo, evento, mensaje -> {
                    mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    // El nombre de clase Java no debe viajar: los consumidores son otros servicios
                    mensaje.getMessageProperties().getHeaders().remove("__TypeId__");
                    mensaje.getMessageProperties().setMessageId(evento.id().toString());
                    return mensaje;
                });
                operaciones.waitForConfirmsOrDie(timeoutConfirmacion.toMillis());
                return null;
            });
        } catch (AmqpException | IllegalStateException e) {
            // Detalle solo en el log del servidor; el cliente recibe un 503 genérico.
            log.error("No se pudo publicar el evento {} ({}): {}", evento.id(), tipo, e.getClass().getSimpleName());
            throw new EventoNoPublicadoException(e);
        }
        log.info("Evento publicado id={} tipo={}", evento.id(), tipo);
        return evento;
    }
}
