package co.edu.unbosque.biohub.consultapublica.config;

import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.RabbitRetryTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.policy.SimpleRetryPolicy;

/**
 * Topología broker del CONSUMIDOR. Este servicio declara (y solo esto):
 * exchange {@code biohub.eventos}, DLX {@code biohub.eventos.dlx}, su cola, su DLQ ({@code <cola>.dlq})
 * y los bindings. No registra MessageConverter: el cuerpo JSON se interpreta a mano en ConsumidorEventos
 * (ignora la cabecera __TypeId__ de otros servicios, que no existe como clase aquí). Los argumentos de la cola ({@code x-dead-letter-*}) deben coincidir en cualquier
 * declaración repetida. Los mensajes rechazados sin reencolar llegan a la DLQ vía el DLX.
 */
@Configuration
public class RabbitConfig {

    private static final Logger log = LoggerFactory.getLogger(RabbitConfig.class);

    /** Patrones de routing key a los que se suscribe la cola. */
    static final String[] PATRONES = {"prueba.#", "hallazgo.#"};

    @Bean
    TopicExchange eventosExchange(@Value("${biohub.eventos.exchange}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }

    @Bean
    TopicExchange eventosDlx(@Value("${biohub.eventos.dlx}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }

    @Bean
    Queue colaEventos(@Value("${biohub.eventos.cola}") String cola,
                      @Value("${biohub.eventos.dlx}") String dlx) {
        return QueueBuilder.durable(cola)
                .deadLetterExchange(dlx)
                .deadLetterRoutingKey(cola + ".dlq")
                .build();
    }

    @Bean
    Queue colaEventosDlq(@Value("${biohub.eventos.cola}") String cola) {
        return QueueBuilder.durable(cola + ".dlq").build();
    }

    @Bean
    Declarables bindingsEventos(Queue colaEventos, Queue colaEventosDlq,
                                TopicExchange eventosExchange, TopicExchange eventosDlx) {
        java.util.List<Binding> bindings = new java.util.ArrayList<>();
        for (String patron : PATRONES) {
            bindings.add(BindingBuilder.bind(colaEventos).to(eventosExchange).with(patron));
        }
        bindings.add(BindingBuilder.bind(colaEventosDlq).to(eventosDlx).with(colaEventosDlq.getName()));
        return new Declarables(bindings);
    }

    /**
     * Reintentos acotados del listener (ver spring.rabbitmq.listener.simple.retry): los mensajes
     * inválidos NO se reintentan; los demás fallos se reintentan y luego van a la DLQ.
     */
    @Bean
    RabbitRetryTemplateCustomizer reintentosListener() {
        return (destino, plantilla) -> {
            if (destino == RabbitRetryTemplateCustomizer.Target.LISTENER) {
                plantilla.setRetryPolicy(new SimpleRetryPolicy(3,
                        Map.of(AmqpRejectAndDontRequeueException.class, false), true, true));
            }
        };
    }

    /**
     * Al agotar reintentos rechaza sin reencolar (→ DLQ), como RejectAndDontRequeueRecoverer pero
     * SIN registrar el cuerpo del mensaje en el log (podría contener datos sensibles).
     */
    @Bean
    MessageRecoverer recuperadorSinCuerpo() {
        return (mensaje, causa) -> {
            Throwable raiz = causa.getCause() != null ? causa.getCause() : causa;
            log.warn("Evento rechazado (a DLQ) messageId={} routingKey={} motivo={}",
                    mensaje.getMessageProperties().getMessageId(),
                    mensaje.getMessageProperties().getReceivedRoutingKey(),
                    raiz instanceof AmqpRejectAndDontRequeueException ? raiz.getMessage() : raiz.getClass().getSimpleName());
            throw new ListenerExecutionFailedException("Reintentos agotados",
                    new AmqpRejectAndDontRequeueException(causa), mensaje);
        };
    }
}
