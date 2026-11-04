package co.edu.unbosque.biohub.consultapublica.service;

/**
 * Strategy de la búsqueda pública (SAD §6.5, ARQ-06, ASR1/RNF1). Decide qué parte de lo que pidió el visitante se
 * aplica: {@link BusquedaCompleta} lo aplica todo y {@link BusquedaDegradada} desactiva los filtros avanzados
 * (familia y departamento) para aliviar la base de lectura durante un pico. La estrategia se elige en cada
 * petición ({@link ElectorEstrategiaBusqueda}) según la carga.
 */
public interface EstrategiaBusqueda {

    /** Nombre estable para el estado en actuator y los logs: {@code completa} o {@code degradada}. */
    String nombre();

    /**
     * Arma el criterio definitivo a partir de parámetros ya validados. {@code q} y la paginación se respetan siempre.
     *
     * @param departamentoCodigo código DANE ya resuelto, o {@code null}
     */
    CriterioBusquedaPublica criterio(String q, String familia, String departamentoCodigo, int page, int size);
}
