package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.AuditoriaAccesoUbicacion;

/**
 * Auditoría de accesos a ubicaciones (D28): log estructurado dedicado, sin coordenadas.
 *
 * <p>Implementación en {@code infrastructure} del puerto {@link AuditoriaAccesoUbicacion} (ARQ-28).
 *
 * <p>El logger {@code biohub.auditoria.ubicacion} se envía a un archivo propio, de solo anexado y con rotación y
 * retención amplias, y su nivel queda fijado en INFO en {@code logback-spring.xml} (SEC-J-08 punto 3). <b>El nivel no
 * debe bajarse</b> (OFF, WARN, ERROR): apagaría la auditoría. No definir
 * {@code logging.level.biohub.auditoria.ubicacion} en ningún entorno.
 */
@Component
public class AuditoriaAccesoUbicacionLog implements AuditoriaAccesoUbicacion {

    private static final Logger auditoria = LoggerFactory.getLogger("biohub.auditoria.ubicacion");
    private static final int MAX_FILTROS = 300;
    private static final int MAX_ENDPOINT = 200;

    @Override
    public void registrar(String usuario, String endpoint, boolean permitido, String filtros, int cantidad, int omitidos) {
        auditoria.info("usuario={} endpoint={} resultado={} filtros=[{}] cantidad={} omitidos={}",
                limpiar(usuario, 100), limpiar(endpoint, MAX_ENDPOINT), permitido ? "PERMITIDO" : "DENEGADO",
                limpiar(filtros, MAX_FILTROS), cantidad, omitidos);
    }

    /** Evita inyección de líneas en el log y limita el largo. */
    static String limpiar(String texto, int max) {
        if (texto == null) {
            return "";
        }
        String limpio = texto.replaceAll("[\\p{Cntrl}]", "_");
        return limpio.length() > max ? limpio.substring(0, max) : limpio;
    }
}
