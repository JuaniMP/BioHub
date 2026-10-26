package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Auditoría de accesos a ubicaciones (D28): log estructurado dedicado, sin coordenadas. */
@Component
public class AuditoriaAccesoUbicacion {

    private static final Logger auditoria = LoggerFactory.getLogger("biohub.auditoria.ubicacion");
    private static final int MAX_FILTROS = 300;

    public void registrar(String usuario, String endpoint, boolean permitido, String filtros, int cantidad, int omitidos) {
        auditoria.info("usuario={} endpoint={} resultado={} filtros=[{}] cantidad={} omitidos={}",
                limpiar(usuario, 100), endpoint, permitido ? "PERMITIDO" : "DENEGADO",
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
