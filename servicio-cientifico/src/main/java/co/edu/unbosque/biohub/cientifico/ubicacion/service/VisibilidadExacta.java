package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.util.Optional;

import org.springframework.security.core.Authentication;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.FilaHallazgo;

/**
 * Estrategia de {@code cientifico_alto_rango} (RF12, D21): coordenadas exactas. Es la ÚNICA que recibe el
 * {@link ServicioDescifrado}, que además vuelve a exigir el rol antes de descifrar (segunda barrera).
 */
public final class VisibilidadExacta implements EstrategiaVisibilidadUbicacion {

    private final ServicioDescifrado descifrado;

    public VisibilidadExacta(ServicioDescifrado descifrado) {
        this.descifrado = descifrado;
    }

    @Override
    public UbicacionResuelta resolver(Authentication autenticacion, FilaHallazgo fila) {
        if (!fila.tieneCoordenadas()) {
            return new UbicacionResuelta(UbicacionResuelta.Estado.SIN_COORDENADAS, fila.departamento(),
                    fila.departamentoCodigo(), fila.region(), null, null);
        }
        Optional<Coordenadas> c = fila.cifradas() == null
                ? Optional.empty()
                : descifrado.descifrar(autenticacion, fila.id(), fila.cifradas());
        return c.map(x -> new UbicacionResuelta(UbicacionResuelta.Estado.VISIBLES, fila.departamento(),
                        fila.departamentoCodigo(), fila.region(), x.latitud(), x.longitud()))
                .orElseGet(() -> new UbicacionResuelta(UbicacionResuelta.Estado.NO_DISPONIBLES, fila.departamento(),
                        fila.departamentoCodigo(), fila.region(), null, null));
    }

    @Override
    public boolean necesitaCoordenadasCifradas() {
        return true;
    }
}
