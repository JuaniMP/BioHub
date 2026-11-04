package co.edu.unbosque.biohub.cientifico.ubicacion;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.RegistroUbicacionComando;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.UbicacionService;

/**
 * Base de las pruebas con MySQL 8.4 real en Testcontainers (nunca las BD de las VMs). El esquema lo crea
 * Flyway y Hibernate lo valida ({@code ddl-auto: validate}) al arrancar el contexto. Las llaves se
 * generan aquí, solo para pruebas.
 */
@SpringBootTest(classes = AppPruebaUbicacion.class)
@AutoConfigureMockMvc
public abstract class BaseUbicacionMySqlTest {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cientifico").withUsername("test").withPassword("test");
    protected static final String K1 = llave();
    protected static final String K2 = llave();

    static {
        MYSQL.start(); // contenedor compartido por todas las clases de prueba; Ryuk lo elimina al terminar
    }

    private static String llave() {
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        return java.util.Base64.getEncoder().encodeToString(b);
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("biohub.ubicacion.cifrado.claves", () -> "v1:" + K1 + ",v2:" + K2);
        r.add("biohub.ubicacion.cifrado.clave-activa", () -> "v1");
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected TransactionTemplate tx;
    @Autowired
    protected UbicacionService ubicacion;
    @MockitoBean
    protected JwtDecoder jwtDecoder;

    private static final AtomicInteger SECUENCIA = new AtomicInteger();

    @BeforeEach
    void limpiarTabla() {
        jdbc.update("DELETE FROM ubicacion_hallazgo");
    }

    /** Registra un token falso asociado a los roles y devuelve la cabecera Authorization. */
    protected String bearer(String usuario, String... roles) {
        String token = "token-" + SECUENCIA.incrementAndGet();
        Jwt jwt = Jwt.withTokenValue(token).header("alg", "RS256").subject("sub-" + usuario)
                .claim("preferred_username", usuario)
                .claim("realm_access", Map.of("roles", Arrays.asList(roles)))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        given(jwtDecoder.decode(token)).willReturn(jwt);
        return "Bearer " + token;
    }

    protected String bearerVencido() {
        given(jwtDecoder.decode(anyString())).willThrow(new BadJwtException("Jwt expired"));
        return "Bearer vencido";
    }

    protected static final List<String> ROLES_INTERNOS = List.of(
            "investigador_campo", "auditor_cientifico", "cientifico_alto_rango", "analista_ambiental", "administrador");

    protected UUID sembrar(String nombre, LocalDate fecha, String investigador, String departamento,
                           EstadoConservacion estado, Double lat, Double lon) {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> ubicacion.registrar(
                new RegistroUbicacionComando(id, 1, nombre, fecha, investigador, departamento, estado, lat, lon)));
        return id;
    }
}
