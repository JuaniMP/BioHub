package co.edu.unbosque.biohub.consultapublica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.biohub.consultapublica.domain.EstadoConservacion;
import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.soporte.Contenedores;

/**
 * RNF1/RNF5, ASR1: con Redis caído el servicio sigue respondiendo desde MySQL, rápido, y Redis no afecta el
 * health. Usa su PROPIO Redis (lo detiene) y su propio contexto sin consumidor de cola, para no interferir con
 * las demás pruebas.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"spring.application.name=servicio-consulta-publica",
        "spring.rabbitmq.listener.simple.auto-startup=false"})
@AutoConfigureMockMvc
@DirtiesContext
class RedisCaidoIntegracionTest {

    private static final GenericContainer<?> REDIS_PROPIO = Contenedores.nuevoRedis();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        Contenedores.registrarMysql(r);
        Contenedores.registrarRedis(r, REDIS_PROPIO);
        Contenedores.registrarRabbit(r);
    }

    @MockitoBean
    private JwtDecoder jwtDecoder;
    @Autowired
    private MockMvc mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private ProyectorFichas proyector;
    @Autowired
    private InvalidadorCacheFichas invalidador;

    @Test
    void conRedisCaidoLasConsultasSigueRespondiendoDesdeMysqlYElHealthSigueUp() throws Exception {
        jdbc.sql("DELETE FROM ficha_publica").update();
        UUID cacheada = UUID.nameUUIDFromBytes("cacheada".getBytes());
        UUID nueva = UUID.nameUUIDFromBytes("nueva".getBytes());
        proyector.aplicar(ficha(cacheada, "Atelopus cacheada"));
        mvc.perform(get("/api/publica/fichas/" + cacheada)).andExpect(status().isOk()); // queda en Redis

        REDIS_PROPIO.stop();
        proyector.aplicar(ficha(nueva, "Atelopus nueva")); // fuera de caché

        long inicio = System.nanoTime();
        mvc.perform(get("/api/publica/fichas/" + cacheada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCientifico").value("Atelopus cacheada"));
        mvc.perform(get("/api/publica/fichas/" + nueva))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombreCientifico").value("Atelopus nueva"));
        mvc.perform(get("/api/publica/fichas").param("q", "atelopus"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/publica/fichas/" + UUID.randomUUID())).andExpect(status().isNotFound());
        Duration total = Duration.ofNanos(System.nanoTime() - inicio);
        // Fallo rápido: sin esperar el timeout (300 ms) en cada acceso a la caché
        assertThat(total).isLessThan(Duration.ofSeconds(5));

        // Invalidar con Redis caído no lanza excepción (el evento no debe fallar por la caché)
        invalidador.invalidar(cacheada);

        mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
    }

    private static FichaPublica ficha(UUID id, String nombre) {
        return new FichaPublica(id, 1, nombre, null, "Bufonidae", null, LocalDate.of(2026, 3, 14),
                EstadoConservacion.EN_PELIGRO, "15", "Boyacá", "Andina", null);
    }
}
