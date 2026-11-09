package co.edu.unbosque.biohub.gateway.soporte;

import org.testcontainers.containers.MySQLContainer;

/** MySQL con las mismas reglas estrictas de Aiven: PK obligatoria y sql_mode ANSI + TRADITIONAL. */
public final class ContenedoresGateway {

    private ContenedoresGateway() {
    }

    public static MySQLContainer<?> mysqlComoAiven() {
        return new MySQLContainer<>("mysql:8.4")
                .withDatabaseName("gateway")
                .withCommand("--sql-require-primary-key=ON", "--sql-mode=ANSI,TRADITIONAL",
                        "--default-time-zone=+00:00");
    }
}
