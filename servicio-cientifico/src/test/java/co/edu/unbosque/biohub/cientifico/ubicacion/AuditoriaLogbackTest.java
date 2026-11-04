package co.edu.unbosque.biohub.cientifico.ubicacion;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.util.LogbackMDCAdapter;

/**
 * SEC-J-08 (3): {@code logback-spring.xml} envia el logger de auditoria a un archivo propio, anexado, y fija su nivel
 * en INFO. Se carga con Joran puro (sin contexto de Spring) en un {@link LoggerContext} aislado, apuntando el archivo
 * a un directorio temporal para no ensuciar el repositorio.
 */
class AuditoriaLogbackTest {

    private static final String PROPIEDAD = "UBICACION_AUDITORIA_LOG";

    private LoggerContext contexto;

    @AfterEach
    void limpiar() {
        System.clearProperty(PROPIEDAD);
        if (contexto != null) {
            contexto.stop();
        }
    }

    private LoggerContext cargar(Path archivo) throws Exception {
        System.setProperty(PROPIEDAD, archivo.toString());
        URL xml = getClass().getClassLoader().getResource("logback-spring.xml");
        assertThat(xml).as("logback-spring.xml en src/main/resources").isNotNull();
        contexto = new LoggerContext();
        contexto.setMDCAdapter(new LogbackMDCAdapter()); // un LoggerContext suelto no trae adaptador de MDC
        JoranConfigurator configurador = new JoranConfigurator();
        configurador.setContext(contexto);
        configurador.doConfigure(xml);
        return contexto;
    }

    @Test
    @DisplayName("SEC-J-08: las lineas de biohub.auditoria.ubicacion van a su propio archivo y solo ellas")
    void laAuditoriaVaAUnArchivoPropio(@TempDir Path tmp) throws Exception {
        Path archivo = tmp.resolve("auditoria.log");
        LoggerContext ctx = cargar(archivo);

        ctx.getLogger("biohub.auditoria.ubicacion").info("usuario=ana endpoint=GET /mapa resultado=PERMITIDO");
        ctx.getLogger("co.edu.unbosque.otro").info("mensaje-del-log-general");
        ctx.stop();

        String contenido = Files.readString(archivo);
        assertThat(contenido).contains("usuario=ana endpoint=GET /mapa resultado=PERMITIDO")
                .doesNotContain("mensaje-del-log-general");
    }

    @Test
    @DisplayName("SEC-J-08: el archivo es de solo anexado; al reiniciar conserva lo anterior")
    void anexaSinSobrescribir(@TempDir Path tmp) throws Exception {
        Path archivo = tmp.resolve("auditoria.log");
        cargar(archivo).getLogger("biohub.auditoria.ubicacion").info("linea-1");
        contexto.stop();
        cargar(archivo).getLogger("biohub.auditoria.ubicacion").info("linea-2");
        contexto.stop();

        assertThat(Files.readAllLines(archivo)).anyMatch(l -> l.contains("linea-1")).anyMatch(l -> l.contains("linea-2"));
    }

    @Test
    @DisplayName("SEC-J-08: el nivel del logger de auditoria esta fijado en INFO en el XML (no hereda el de la raiz)")
    void elNivelEstaFijadoEnInfo(@TempDir Path tmp) throws Exception {
        LoggerContext ctx = cargar(tmp.resolve("auditoria.log"));
        Logger auditoria = ctx.getLogger("biohub.auditoria.ubicacion");

        assertThat(auditoria.getLevel()).isNotNull();
        assertThat(auditoria.getLevel().toString()).isEqualTo(Level.INFO.name());
        assertThat(auditoria.isInfoEnabled()).isTrue();

        // Aun con la raiz silenciada (ERROR), la auditoria sigue escribiendo
        ctx.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(ch.qos.logback.classic.Level.ERROR);
        auditoria.info("sigue-auditando");
        ctx.stop();
        assertThat(Files.readString(tmp.resolve("auditoria.log"))).contains("sigue-auditando");
    }
}
