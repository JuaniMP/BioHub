package co.edu.unbosque.biohub.cientifico.eventos;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
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
 * QA (manual/opt-in, E2E ARQ-26): publica un hallazgo.registrado con el PublicadorEventos REAL (user_id AMQP fijado por
 * el publicador, sin inyectarlo a mano) contra el broker de desarrollo, para que un consulta-publica en ejecucion con
 * verificacion activa lo proyecte. Requiere QA_RMQ_PORT/USER/PASS y QA_HALLAZGO_ID (UUID).
 */
@EnabledIfEnvironmentVariable(named = "QA_HALLAZGO_ID", matches = ".+")
@SpringBootTest(classes = PublicadorHallazgoE2ETest.Config.class, properties = {
        "biohub.eventos.exchange=biohub.eventos",
        "spring.rabbitmq.host=localhost",
        "spring.rabbitmq.publisher-confirm-type=correlated"})
class PublicadorHallazgoE2ETest {

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

    @Test
    void publicaHallazgoRegistradoConPublicadorReal() {
        EventoSobre e = publicador.publicar("hallazgo.registrado", "qa-e2e", Map.of(
                "hallazgoId", System.getenv("QA_HALLAZGO_ID"), "version", 1,
                "nombreCientifico", "Pristimantis qae2e", "nombreComun", "Rana QA E2E",
                "familia", "Strabomantidae", "tipoEspecimen", "Adulto", "fechaRecoleccion", "2026-03-14",
                "estadoConservacion", "EN_PELIGRO", "departamento", "25", "descripcion", "E2E ARQ-26"));
        assertThat(e.id()).isNotNull();
    }
}
