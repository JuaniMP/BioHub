package co.edu.unbosque.biohub.reportes.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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

import co.edu.unbosque.biohub.reportes.config.RabbitConfig;
import co.edu.unbosque.biohub.reportes.eventos.service.AlmacenEventosRecibidos;
import co.edu.unbosque.biohub.reportes.eventos.service.ConsumidorEventos;

/** QA (opt-in): consumo con el usuario reportes_mq contra el RabbitMQ con las definitions de deploy/backend. */
@EnabledIfEnvironmentVariable(named = "QA_RMQ_PORT", matches = ".+")
@SpringBootTest(classes = ConsumidorBrokerDesplegadoTest.Config.class, properties = {
        "biohub.eventos.exchange=biohub.eventos",
        "biohub.eventos.dlx=biohub.eventos.dlx",
        "biohub.eventos.cola=reportes.eventos",
        "spring.application.name=it",
        "spring.rabbitmq.listener.simple.default-requeue-rejected=false",
        "spring.rabbitmq.listener.simple.retry.enabled=true",
        "spring.rabbitmq.listener.simple.retry.max-attempts=3",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"})
class ConsumidorBrokerDesplegadoTest {

    @Configuration
    @ImportAutoConfiguration({RabbitAutoConfiguration.class, JacksonAutoConfiguration.class})
    @Import({RabbitConfig.class, ConsumidorEventos.class, AlmacenEventosRecibidos.class})
    static class Config {
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.rabbitmq.host", () -> "localhost");
        r.add("spring.rabbitmq.port", () -> System.getenv("QA_RMQ_PORT"));
        r.add("spring.rabbitmq.username", () -> System.getenv("QA_RMQ_USER"));
        r.add("spring.rabbitmq.password", () -> System.getenv("QA_RMQ_PASS"));
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
    void reportesDeclaraTopologiaConsumeYEnviaALaDlqConSusPermisos() {
        // 1) un evento valido enviado por el publicador real (otra JVM, test del cientifico) o por este usuario
        String id = UUID.randomUUID().toString();
        template.send("biohub.eventos", "prueba.ping", json(evento(id, "prueba.ping", "permisos")));
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() ->
                assertThat(almacen.ultimos()).anyMatch(e -> e.id().toString().equals(id)));
        // 2) rechazo -> DLQ (el usuario puede escribir/leer su .dlq)
        template.send("biohub.eventos", "hallazgo.roto", json("{roto"));
        await().atMost(Duration.ofSeconds(15)).until(() -> template.receive("reportes.eventos.dlq") != null);
    }
}
