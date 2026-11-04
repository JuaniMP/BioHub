package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

/**
 * Puerto de auditoría de accesos a ubicaciones (D28, RNF9). Lo usan los servicios, el manejador de errores y el
 * decorador de auditoría; la implementación vive en {@code infrastructure} (ARQ-28: las dependencias apuntan hacia
 * {@code domain}). Las coordenadas NUNCA se pasan aquí: solo usuario, endpoint, resultado y filtros ya saneados.
 */
public interface AuditoriaAccesoUbicacion {

    /**
     * @param usuario    preferred_username de quien consulta
     * @param endpoint   ruta o acción consultada
     * @param permitido  {@code true} si se entregó (o intentó entregar) el dato; {@code false} si se denegó
     * @param filtros    resumen de los filtros o del hallazgo, sin coordenadas
     * @param cantidad   elementos entregados
     * @param omitidos   elementos omitidos (por ejemplo, no descifrables)
     */
    void registrar(String usuario, String endpoint, boolean permitido, String filtros, int cantidad, int omitidos);
}
