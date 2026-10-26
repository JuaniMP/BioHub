package co.edu.unbosque.biohub.consultapublica.soporte;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Contexto completo con MySQL, Redis y RabbitMQ reales (Testcontainers). Todas las subclases comparten el
 * mismo contexto (misma configuración) y por tanto el mismo consumidor de la cola.
 */
@SpringBootTest(properties = {"spring.application.name=servicio-consulta-publica",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms"})
@AutoConfigureMockMvc
public abstract class BaseIntegracion {

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        Contenedores.registrarMysql(r);
        Contenedores.registrarRedis(r, Contenedores.redis());
        Contenedores.registrarRabbit(r);
    }

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcClient jdbc;
    @Autowired
    protected RabbitTemplate rabbit;
    @Autowired
    protected CacheManager cacheManager;

    @BeforeEach
    void limpiarEstado() {
        jdbc.sql("DELETE FROM ficha_publica").update();
        cacheManager.getCache("fichas").clear();
        cacheManager.getCache("busquedas").clear();
        while (rabbit.receive("consulta-publica.eventos.dlq") != null) {
            // vacía la DLQ de pruebas anteriores
        }
    }

    protected void publicar(String tipo, String json) {
        rabbit.send("biohub.eventos", tipo, Eventos.mensaje(json));
    }

    protected Message siguienteDeLaDlq(java.time.Duration espera) {
        long limite = System.nanoTime() + espera.toNanos();
        while (System.nanoTime() < limite) {
            Message m = rabbit.receive("consulta-publica.eventos.dlq");
            if (m != null) {
                return m;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    protected static UUID id(String semilla) {
        return UUID.nameUUIDFromBytes(semilla.getBytes());
    }
}
