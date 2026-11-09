package co.edu.unbosque.biohub.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.biohub.gateway.domain.ApiKey;
import co.edu.unbosque.biohub.gateway.domain.ApiKeyRepository;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorExterno;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorExternoRepository;
import co.edu.unbosque.biohub.gateway.service.GeneradorApiKey;
import co.edu.unbosque.biohub.gateway.service.ProxyService;
import co.edu.unbosque.biohub.gateway.soporte.ContenedoresGateway;
import co.edu.unbosque.biohub.gateway.soporte.ServicioFalso;

/** Con Redis apagado el gateway sigue limitando, autenticando y respondiendo (D25, RNF5, SEC-05). */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.data.redis.host=127.0.0.1",
        "spring.data.redis.port=1",
        "spring.data.redis.password=",
        "biohub.gateway.limites.por-ip-portal=3",
        "biohub.gateway.identidad.client-secret="
})
@ActiveProfiles("test")
class RedisCaidoIntegracionTest {

    @Container
    static final MySQLContainer<?> MYSQL = ContenedoresGateway.mysqlComoAiven();

    private static final ServicioFalso SERVICIO = crearServicio();

    private static ServicioFalso crearServicio() {
        try {
            return new ServicioFalso();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registro.add("spring.datasource.username", MYSQL::getUsername);
        registro.add("spring.datasource.password", MYSQL::getPassword);
        registro.add("spring.flyway.user", MYSQL::getUsername);
        registro.add("spring.flyway.password", MYSQL::getPassword);
        registro.add("biohub.gateway.rutas.publica", SERVICIO::url);
    }

    @AfterAll
    static void apagar() {
        SERVICIO.close();
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private ConsumidorExternoRepository consumidores;

    @Autowired
    private ApiKeyRepository keys;

    @Autowired
    private GeneradorApiKey generador;

    @Autowired
    private ProxyService proxy;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final HttpClient cliente = HttpClient.newHttpClient();

    private int estado(String ruta, String... cabeceras) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + puerto + ruta));
        if (cabeceras.length > 0) {
            peticion.headers(cabeceras);
        }
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @Test
    void elPortalYLaApiExternaSiguenFuncionandoYLimitando() throws Exception {
        // Portal: el respaldo en memoria mantiene el límite de 3.
        assertThat(estado("/api/publica/fichas")).isEqualTo(200);
        assertThat(estado("/api/publica/fichas")).isEqualTo(200);
        assertThat(estado("/api/publica/fichas")).isEqualTo(200);
        assertThat(estado("/api/publica/fichas")).isEqualTo(429);

        // API externa: sin caché la credencial se lee de la base.
        UUID consumidor = UUID.randomUUID();
        java.time.Instant ahora = java.time.Instant.now();
        consumidores.save(new ConsumidorExterno(consumidor, "Sin Redis", "a@b.co", 1, "sub", ahora));
        GeneradorApiKey.KeyGenerada generada = generador.generar();
        keys.save(new ApiKey(UUID.randomUUID(), consumidor, generada.prefijo(), generada.hash(), null, "sub", ahora));

        assertThat(estado("/api/v1/externa/fichas", "X-API-Key", generada.key())).isEqualTo(200);
        assertThat(estado("/api/v1/externa/fichas", "X-API-Key", generada.key())).isEqualTo(429);
        assertThat(estado("/api/v1/externa/fichas")).isEqualTo(401);
    }
}
