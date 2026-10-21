package co.edu.unbosque.biohub.consultapublica.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología broker: todos los servicios publican/consumen eventos en un único exchange topic.
 * Las colas y bindings se declaran con cada historia de usuario que los necesite.
 */
@Configuration
public class RabbitConfig {

    @Bean
    TopicExchange eventosExchange(@Value("${biohub.eventos.exchange}") String nombre) {
        return new TopicExchange(nombre, true, false);
    }

    @Bean
    MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
