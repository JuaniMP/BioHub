package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;

/**
 * Único punto de descifrado para consultas: vuelve a exigir el rol ANTES de pedir el descifrado
 * (segunda barrera, D21). Solo lo recibe {@link VisibilidadExacta}. Descifra por medio del repositorio decorado
 * ({@link RepositorioUbicaciones#obtenerUbicacionExacta}), de modo que cada acceso queda en la auditoría.
 */
@Service
public class ServicioDescifrado {

    private static final Logger log = LoggerFactory.getLogger(ServicioDescifrado.class);

    private final PoliticaAccesoUbicacion politica;
    private final RepositorioUbicaciones repositorio;

    public ServicioDescifrado(PoliticaAccesoUbicacion politica, RepositorioUbicaciones repositorio) {
        this.politica = politica;
        this.repositorio = repositorio;
    }

    /**
     * @return las coordenadas, o vacío si no se pudieron descifrar (llave retirada o dato alterado; se registra en el log)
     * @throws org.springframework.security.access.AccessDeniedException si la autenticación no es de alto rango
     */
    public Optional<Coordenadas> descifrar(Authentication autenticacion, UUID hallazgoId, CoordenadasCifradas cifradas) {
        politica.exigirCoordenadas(autenticacion);
        try {
            return Optional.of(repositorio.obtenerUbicacionExacta(autenticacion.getName(), hallazgoId, cifradas));
        } catch (CoordenadasNoDescifrablesException e) {
            if (e.motivo() == CoordenadasNoDescifrablesException.Motivo.INTEGRIDAD) {
                log.error("Integridad de ubicación fallida hallazgoId={} claveVersion={}", hallazgoId, cifradas.claveVersion());
            } else {
                log.error("Llave retirada para ubicación hallazgoId={} claveVersion={}", hallazgoId, cifradas.claveVersion());
            }
            return Optional.empty();
        }
    }
}
