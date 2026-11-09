package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/** SEC-J-08 (D28): la auditoría de accesos a coordenadas no admite inyección de líneas en ningún campo. */
class AuditoriaAccesoUbicacionLogTest {

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    @BeforeEach
    void capturar() {
        logger = (Logger) LoggerFactory.getLogger("biohub.auditoria.ubicacion");
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void soltar() {
        logger.detachAppender(logs);
    }

    private List<String> lineas() {
        return new ArrayList<>(logs.list).stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("SEC-J-08: usuario, endpoint y filtros pasan por limpiar(): sin CR/LF ni caracteres de control")
    void todosLosCamposSeLimpian() {
        new AuditoriaAccesoUbicacionLog().registrar("juana\nusuario=falso", "GET /api/x\r\nresultado=PERMITIDO\u0000",
                false, "especie=a\nb", 3, 1);
        assertThat(lineas()).singleElement().satisfies(l -> {
            assertThat(l).doesNotContain("\n").doesNotContain("\r").doesNotContain("\u0000");
            assertThat(l).contains("usuario=juana_usuario=falso").contains("endpoint=GET /api/x__resultado=PERMITIDO_")
                    .contains("resultado=DENEGADO").contains("filtros=[especie=a_b]");
        });
    }

    @Test
    @DisplayName("SEC-J-08: el endpoint se trunca para que una URL enorme no inunde el log")
    void elEndpointSeTrunca() {
        new AuditoriaAccesoUbicacionLog().registrar("u", "GET /" + "a".repeat(5_000), true, "", 0, 0);
        assertThat(lineas()).singleElement().satisfies(l -> assertThat(l.length()).isLessThan(500));
    }

    @Test
    void limpiarAceptaNulo() {
        assertThat(AuditoriaAccesoUbicacionLog.limpiar(null, 10)).isEmpty();
    }
}
