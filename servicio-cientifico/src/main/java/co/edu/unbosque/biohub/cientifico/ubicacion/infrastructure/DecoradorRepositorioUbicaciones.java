package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CriterioBusquedaInterna;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.FilaHallazgo;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgo;

/**
 * Base del Decorator (SAD §6.3.2): envuelve a otro {@link RepositorioUbicaciones} y le delega todo; cada
 * subclase sobrescribe solo lo que le interesa.
 */
abstract class DecoradorRepositorioUbicaciones implements RepositorioUbicaciones {

    protected final RepositorioUbicaciones envuelto;

    protected DecoradorRepositorioUbicaciones(RepositorioUbicaciones envuelto) {
        this.envuelto = java.util.Objects.requireNonNull(envuelto, "envuelto");
    }

    @Override
    public boolean existe(UUID hallazgoId) {
        return envuelto.existe(hallazgoId);
    }

    @Override
    public Optional<UbicacionHallazgo> porId(UUID hallazgoId) {
        return envuelto.porId(hallazgoId);
    }

    @Override
    public void guardar(UbicacionHallazgo fila, Coordenadas coordenadas) {
        envuelto.guardar(fila, coordenadas);
    }

    @Override
    public long contar(CriterioBusquedaInterna criterio) {
        return envuelto.contar(criterio);
    }

    @Override
    public List<FilaHallazgo> buscar(CriterioBusquedaInterna criterio, int offset, int limite, boolean conCifrado) {
        return envuelto.buscar(criterio, offset, limite, conCifrado);
    }

    @Override
    public Coordenadas obtenerUbicacionExacta(String usuario, UUID hallazgoId, CoordenadasCifradas cifradas) {
        return envuelto.obtenerUbicacionExacta(usuario, hallazgoId, cifradas);
    }
}
