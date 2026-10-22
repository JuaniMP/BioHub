package co.edu.unbosque.biohub.cientifico.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import co.edu.unbosque.biohub.cientifico.config.RabbitConfig;
import co.edu.unbosque.biohub.cientifico.eventos.domain.EventoSobre;
import co.edu.unbosque.biohub.cientifico.eventos.service.PublicadorEventos;

/**
 * QA (manual/opt-in): valida los PERMISOS del usuario cientifico_mq contra un RabbitMQ con las definitions de
 * deploy/backend (SEC-04). Solo corre si QA_RMQ_PORT esta definido; credenciales por QA_RMQ_USER / QA_RMQ_PASS.
 */
@EnabledIfEnvironmentVariable(named = "QA_RMQ_PORT", matches = ".+")
@SpringBootTest(classes = PublicadorBrokerDesplegadoTest.Config.class, properties = {
        "biohub.eventos.exchange=biohub.eventos",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.publisher-confirm-type=correlated"})
class PublicadorBrokerDesplegadoTest {

    @Configuration
    @ImportAutoConfiguration({RabbitAutoConfiguration.class, JacksonAutoConfiguration.class})
    @Import({RabbitConfig.class, PublicadorEventos.class})
    static class Config {
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.rabbitmq.port", () -> System.getenv("QA_RMQ_PORT"));
        r.add("spring.rabbitmq.username", () -> System.getenv("QA_RMQ_USER"));
        r.add("spring.rabbitmq.password", () -> System.getenv("QA_RMQ_PASS"));
    }

    @Autowired
    private PublicadorEventos publicador;
    @Autowired
    private RabbitTemplate template;

    @Test
    void cientificoPublicaConConfirmacionYNoPuedeLeerColas() {
        EventoSobre e = publicador.publicar("prueba.ping", "qa-e2e", Map.of("mensaje", "permisos"));
        assertThat(e.id()).isNotNull();
        assertThatThrownBy(() -> template.receive("reportes.eventos", 2000)).isInstanceOf(AmqpException.class);
    }
}
