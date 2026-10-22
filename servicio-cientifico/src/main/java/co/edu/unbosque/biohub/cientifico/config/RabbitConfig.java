package co.edu.unbosque.biohub.cientifico.config;

import java.time.Clock;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Topología broker del PUBLICADOR: solo declara el exchange topic durable {@code biohub.eventos}
 * (el usuario RabbitMQ de este servicio solo tiene configure/write sobre ese exchange).
 * Cada consumidor declara su propia cola, DLQ y bindings.
 */
@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange eventosExchange(@Value("${biohub.eventos.exchange}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }

    /** JSON con el ObjectMapper de Spring Boot (fechas ISO-8601, no timestamps). */
    @Bean
    MessageConverter jsonMessageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
