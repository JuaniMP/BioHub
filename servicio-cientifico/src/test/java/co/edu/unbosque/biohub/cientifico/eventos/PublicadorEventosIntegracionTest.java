package co.edu.unbosque.biohub.cientifico.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
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
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.cientifico.config.RabbitConfig;
import co.edu.unbosque.biohub.cientifico.eventos.domain.EventoSobre;
import co.edu.unbosque.biohub.cientifico.eventos.service.EventoNoPublicadoException;
import co.edu.unbosque.biohub.cientifico.eventos.service.PublicadorEventos;

/** Integración con un RabbitMQ real: publisher confirms, JSON, persistencia y routing key. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = PublicadorEventosIntegracionTest.Config.class, properties = {
        "biohub.eventos.exchange=biohub.eventos",
        "spring.rabbitmq.publisher-confirm-type=correlated"})
class PublicadorEventosIntegracionTest {

    @Configuration
    @ImportAutoConfiguration({RabbitAutoConfiguration.class, JacksonAutoConfiguration.class})
    @Import({RabbitConfig.class, PublicadorEventos.class})
    static class Config {
    }

    @Container
    static final RabbitMQContainer RABBIT = new RabbitMQContainer("rabbitmq:4-management");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.rabbitmq.host", RABBIT::getHost);
        r.add("spring.rabbitmq.port", RABBIT::getAmqpPort);
        r.add("spring.rabbitmq.username", RABBIT::getAdminUsername);
        r.add("spring.rabbitmq.password", RABBIT::getAdminPassword);
    }

    @Autowired
    private PublicadorEventos publicador;
    @Autowired
    private RabbitTemplate template;
    @Autowired
    private RabbitAdmin admin;
    @Autowired
    private TopicExchange eventosExchange;
    @Autowired
    private ObjectMapper mapper;

    @Test
    void publicaJsonPersistenteConRoutingKeyDelTipo() throws Exception {
        Queue cola = new Queue("it.cola", true, false, false);
        admin.declareQueue(cola);
        admin.declareBinding(BindingBuilder.bind(cola).to(eventosExchange).with("prueba.#"));

        EventoSobre evento = publicador.publicar("prueba.ping", "admin1", Map.of("mensaje", "hola"));

        Message m = template.receive("it.cola", 5000);
        assertThat(m).isNotNull();
        assertThat(m.getMessageProperties().getReceivedRoutingKey()).isEqualTo("prueba.ping");
        assertThat(m.getMessageProperties().getReceivedDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(m.getMessageProperties().getHeaders()).doesNotContainKey("__TypeId__");
        assertThat(m.getMessageProperties().getContentType()).isEqualTo("application/json");
        JsonNode json = mapper.readTree(m.getBody());
        assertThat(json.get("id").asText()).isEqualTo(evento.id().toString());
        assertThat(json.get("tipo").asText()).isEqualTo("prueba.ping");
        assertThat(json.get("origen").asText()).isEqualTo("servicio-cientifico");
        assertThat(json.get("usuario").asText()).isEqualTo("admin1");
        assertThat(json.get("fecha").asText()).matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
        assertThat(json.get("datos").get("mensaje").asText()).isEqualTo("hola");
    }

    @Test
    void sinBrokerLanzaEventoNoPublicado() {
        // Puerto cerrado: nadie escucha en el 1 de localhost
        CachingConnectionFactory cf = new CachingConnectionFactory("localhost", 1);
        cf.setConnectionTimeout(1000);
        cf.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        PublicadorEventos sinBroker = new PublicadorEventos(new RabbitTemplate(cf), "biohub.eventos",
                Duration.ofSeconds(1), Clock.systemUTC());
        try {
            assertThatThrownBy(() -> sinBroker.publicar("prueba.ping", "admin1", Map.of("mensaje", "x")))
                    .isInstanceOf(EventoNoPublicadoException.class);
        } finally {
            cf.destroy();
        }
    }
}
