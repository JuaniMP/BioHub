package co.edu.unbosque.biohub.consultapublica.soporte;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * Contenedores efímeros compartidos por todas las pruebas de integración de la JVM (D30). Nunca se conectan a
 * las BD, colas ni Redis de las VMs. Se inician una sola vez, bajo demanda, y Ryuk los elimina al terminar.
 */
public final class Contenedores {

    private static GenericContainer<?> mysql;
    private static GenericContainer<?> redis;
    private static RabbitMQContainer rabbit;

    private Contenedores() {
    }

    public static synchronized GenericContainer<?> mysql() {
        if (mysql == null) {
            mysql = new GenericContainer<>("mysql:8.4")
                    .withEnv("MYSQL_ROOT_PASSWORD", "prueba")
                    .withEnv("MYSQL_DATABASE", "consulta_publica")
                    .withExposedPorts(3306)
                    // El arranque imprime "ready for connections" dos veces; solo la segunda lleva "port: 3306"
                    .waitingFor(Wait.forLogMessage(".*ready for connections.*port: 3306.*", 1));
            mysql.start();
        }
        return mysql;
    }

    public static synchronized GenericContainer<?> redis() {
        if (redis == null) {
            redis = nuevoRedis();
        }
        return redis;
    }

    /** Un Redis nuevo y exclusivo (por ejemplo, para una prueba que lo detiene). */
    public static GenericContainer<?> nuevoRedis() {
        GenericContainer<?> r = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*", 1));
        r.start();
        return r;
    }

    public static synchronized RabbitMQContainer rabbit() {
        if (rabbit == null) {
            rabbit = new RabbitMQContainer("rabbitmq:4-management");
            rabbit.start();
        }
        return rabbit;
    }

    public static void registrarMysql(DynamicPropertyRegistry r) {
        GenericContainer<?> m = mysql();
        r.add("spring.datasource.url", () -> "jdbc:mysql://" + m.getHost() + ":" + m.getMappedPort(3306)
                + "/consulta_publica");
        r.add("spring.datasource.username", () -> "root");
        r.add("spring.datasource.password", () -> "prueba");
    }

    public static void registrarRedis(DynamicPropertyRegistry r, GenericContainer<?> redis) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
    }

    public static void registrarRabbit(DynamicPropertyRegistry r) {
        RabbitMQContainer q = rabbit();
        r.add("spring.rabbitmq.host", q::getHost);
        r.add("spring.rabbitmq.port", q::getAmqpPort);
        r.add("spring.rabbitmq.username", q::getAdminUsername);
        r.add("spring.rabbitmq.password", q::getAdminPassword);
    }
}
