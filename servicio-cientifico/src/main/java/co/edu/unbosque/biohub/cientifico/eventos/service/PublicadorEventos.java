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
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.cientifico.eventos.domain.EventoSobre;

/** Publica eventos persistentes en el exchange topic, esperando la confirmación (publisher confirm) del broker. */
@Service
public class PublicadorEventos {

    static final String ORIGEN = "servicio-cientifico";
    private static final String PREFIJO_HALLAZGO = "hallazgo.";
    private static final Logger log = LoggerFactory.getLogger(PublicadorEventos.class);

    private final RabbitTemplate rabbitTemplate;
    private final String exchange;
    private final Duration timeoutConfirmacion;
    private final Clock reloj;
    /** Usuario AMQP con el que este servicio se conecta al broker; viaja como {@code user_id} (ARQ-13, ARQ-26). */
    private final String usuarioAmqp;

    public PublicadorEventos(RabbitTemplate rabbitTemplate,
                             @Value("${biohub.eventos.exchange}") String exchange,
                             @Value("${biohub.eventos.timeout-confirmacion:5s}") Duration timeoutConfirmacion,
                             Clock reloj) {
        this.rabbitTemplate = rabbitTemplate;
        this.exchange = exchange;
        this.timeoutConfirmacion = timeoutConfirmacion;
        this.reloj = reloj;
        this.usuarioAmqp = usuarioDeLaConexion(rabbitTemplate);
    }

    /**
     * El {@code user_id} sale de la misma {@code ConnectionFactory} con la que se publica
     * ({@code spring.rabbitmq.username}), no de una constante: RabbitMQ rechaza un {@code user_id} distinto del
     * usuario autenticado, por lo que consulta-publica puede confiar en el valor (SEC-J-01, SEC-J-11).
     */
    private static String usuarioDeLaConexion(RabbitTemplate plantilla) {
        ConnectionFactory fabrica = plantilla.getConnectionFactory();
        String usuario = fabrica == null ? null : fabrica.getUsername();
        if (usuario == null || usuario.isBlank()) {
            throw new IllegalStateException("No se pudo determinar el usuario AMQP de la conexion (spring.rabbitmq.username)");
        }
        return usuario;
    }

    /**
     * Publica un evento con routing key = tipo.
     *
     * <p>Los {@code hallazgo.*} nunca llevan coordenadas (D14, RNF9, SEC-J-13, D46): se revisan claves y textos, en
     * cualquier profundidad, ANTES de armar el sobre. Esta es la última barrera que impide que el mensaje exista.
     *
     * @throws IllegalArgumentException   si {@code tipo} es {@code hallazgo.*} y {@code datos} contiene coordenadas
     * @throws EventoNoPublicadoException si el broker no responde o no confirma
     */
    public EventoSobre publicar(String tipo, String usuario, Map<String, Object> datos) {
        if (tipo != null && tipo.startsWith(PREFIJO_HALLAZGO) && DetectorCoordenadas.contiene(datos)) {
            // Sin datos en el mensaje: ni el valor ni la clave que coincidió deben llegar al log
            throw new IllegalArgumentException("hallazgo.* no puede llevar coordenadas (D14)");
        }
        EventoSobre evento = new EventoSobre(UUID.randomUUID(), tipo, ORIGEN, Instant.now(reloj), usuario, datos);
        try {
            rabbitTemplate.invoke(operaciones -> {
                operaciones.convertAndSend(exchange, tipo, evento, mensaje -> {
                    mensaje.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                    // El nombre de clase Java no debe viajar: los consumidores son otros servicios
                    mensaje.getMessageProperties().getHeaders().remove("__TypeId__");
                    mensaje.getMessageProperties().setMessageId(evento.id().toString());
                    // Identidad del publicador: el broker comprueba que coincida con el usuario autenticado
                    mensaje.getMessageProperties().setUserId(usuarioAmqp);
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
