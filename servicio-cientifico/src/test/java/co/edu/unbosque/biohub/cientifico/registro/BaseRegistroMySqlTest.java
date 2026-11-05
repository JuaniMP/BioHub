package co.edu.unbosque.biohub.cientifico.registro;

import java.security.SecureRandom;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

/**
 * Base de las pruebas de registro contra un MySQL 8.4 real en Testcontainers, nunca contra las bases del
 * equipo. Flyway crea el esquema e Hibernate lo valida al arrancar ({@code ddl-auto: validate}), así que
 * si una entidad y su migración no coinciden, el contexto ni siquiera levanta.
 *
 * <p>Requiere Docker en marcha.
 */
@SpringBootTest(classes = AppPruebaRegistro.class)
public abstract class BaseRegistroMySqlTest {

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cientifico").withUsername("test").withPassword("test");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        // Ubicación necesita una llave para arrancar; se genera aquí y solo vive en la prueba.
        r.add("biohub.ubicacion.cifrado.claves", () -> "v1:" + llaveDePrueba());
        r.add("biohub.ubicacion.cifrado.clave-activa", () -> "v1");
    }

    private static String llaveDePrueba() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected TransactionTemplate tx;

    /** Deja las tablas limpias respetando el orden de las claves foráneas. */
    @BeforeEach
    void limpiarTablas() {
        jdbc.update("DELETE FROM hallazgo_auditoria");
        jdbc.update("DELETE FROM hallazgo_version");
        jdbc.update("DELETE FROM hallazgo_evidencia");
        jdbc.update("DELETE FROM ubicacion_hallazgo");
        jdbc.update("DELETE FROM hallazgo");
        jdbc.update("DELETE FROM ficha_plantilla");
    }
}
