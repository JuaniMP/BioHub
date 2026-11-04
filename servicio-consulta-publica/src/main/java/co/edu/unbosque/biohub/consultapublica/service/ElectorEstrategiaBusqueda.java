package co.edu.unbosque.biohub.consultapublica.service;

import org.springframework.stereotype.Component;

/** Contexto del Strategy: en cada petición elige la búsqueda completa o la degradada según el {@link MonitorCarga}. */
@Component
public class ElectorEstrategiaBusqueda {

    private final EstrategiaBusqueda completa = new BusquedaCompleta();
    private final EstrategiaBusqueda degradada = new BusquedaDegradada();
    private final MonitorCarga monitor;

    public ElectorEstrategiaBusqueda(MonitorCarga monitor) {
        this.monitor = monitor;
    }

    public EstrategiaBusqueda actual() {
        return monitor.degradado() ? degradada : completa;
    }
}
