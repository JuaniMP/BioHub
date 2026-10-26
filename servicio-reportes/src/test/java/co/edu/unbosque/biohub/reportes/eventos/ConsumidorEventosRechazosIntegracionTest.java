package co.edu.unbosque.biohub.reportes.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
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

import co.edu.unbosque.biohub.reportes.config.RabbitConfig;
import co.edu.unbosque.biohub.reportes.eventos.service.AlmacenEventosRecibidos;
import co.edu.unbosque.biohub.reportes.eventos.service.ConsumidorEventos;

/** Integración con un RabbitMQ real: topología, enrutamiento por patrón, consumo y DLQ. */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(classes = ConsumidorEventosRechazosIntegracionTest.Config.class, properties = {
        "biohub.eventos.exchange=biohub.eventos",
        "biohub.eventos.dlx=biohub.eventos.dlx",
        "biohub.eventos.cola=reportes.eventos",
        "spring.application.name=it",
        "spring.rabbitmq.listener.simple.default-requeue-rejected=false",
        "spring.rabbitmq.listener.simple.retry.enabled=true",
        "spring.rabbitmq.listener.simple.retry.max-attempts=3",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"})
class ConsumidorEventosRechazosIntegracionTest {

    @Configuration
    @ImportAutoConfiguration({RabbitAutoConfiguration.class, JacksonAutoConfiguration.class})
    @Import({RabbitConfig.class, ConsumidorEventos.class, AlmacenEventosRecibidos.class})
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
    private RabbitTemplate template;
    @Autowired
    private AlmacenEventosRecibidos almacen;

    private static Message json(String cuerpo) {
        MessageProperties p = new MessageProperties();
        p.setContentType("application/json");
        // Como publica servicio-cientifico en el flujo real: cabecera con una clase que aquí no existe
        p.setHeader("__TypeId__", "co.edu.unbosque.biohub.cientifico.eventos.domain.EventoSobre");
        return MessageBuilder.withBody(cuerpo.getBytes()).andProperties(p).build();
    }

    private static String evento(String id, String tipo, String mensaje) {
        return """
                {"id":"%s","tipo":"%s","origen":"servicio-cientifico","fecha":"2026-10-21T12:00:00Z",
                 "usuario":"admin1","datos":{"mensaje":"%s"}}""".formatted(id, tipo, mensaje);
    }

    @Test
    void rechazaALaDlqRoutingKeyDistintaDelTipoYCuerpoMayorA64KB() {
        // Entra a la cola (binding hallazgo.#) pero el tipo del sobre no coincide con la routing key
        template.send("biohub.eventos", "hallazgo.registrado",
                json(evento(UUID.randomUUID().toString(), "prueba.ping", "x")));
        // Mas de 64 KB (routing key valida, JSON valido pero enorme)
        template.send("biohub.eventos", "prueba.ping",
                json(evento(UUID.randomUUID().toString(), "prueba.ping", "a".repeat(70_000))));
        java.util.List<Message> enDlq = new java.util.ArrayList<>();
        await().atMost(Duration.ofSeconds(15)).until(() -> {
            Message m = template.receive("reportes.eventos.dlq");
            if (m != null) {
                enDlq.add(m);
            }
            return enDlq.size() >= 2;
        });
        assertThat(enDlq).hasSize(2);
        assertThat(almacen.ultimos()).isEmpty();
        assertThat(template.receive("reportes.eventos")).isNull();
    }
}
