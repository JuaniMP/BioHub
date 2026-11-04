package co.edu.unbosque.biohub.consultapublica.service;

import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.consultapublica.config.CacheConfig;
import co.edu.unbosque.biohub.consultapublica.service.vista.FichaPublicaResponse;
import co.edu.unbosque.biohub.consultapublica.service.vista.FichaResumenResponse;
import co.edu.unbosque.biohub.consultapublica.service.vista.PaginaFichasResponse;
import co.edu.unbosque.biohub.consultapublica.infrastructure.BusquedaFichasJdbc;

/**
 * Lecturas públicas con caché Redis (D25). {@code sync = true}: cuando expira una clave viral solo una consulta
 * a MySQL por clave e instancia (evita la estampida). Las excepciones (404) no se cachean. Si Redis falla, el
 * {@code CacheErrorHandler} lo trata como miss y se lee de MySQL.
 */
@Service
public class ConsultaFichasService {

    private final BusquedaFichasJdbc repositorio;

    public ConsultaFichasService(BusquedaFichasJdbc repositorio) {
        this.repositorio = repositorio;
    }

    @Cacheable(cacheNames = CacheConfig.BUSQUEDAS, key = "#criterio.claveCache()", sync = true)
    public PaginaFichasResponse buscar(CriterioBusquedaPublica criterio) {
        long total = repositorio.contar(criterio);
        List<FichaResumenResponse> contenido = total > criterio.offset() ? repositorio.buscar(criterio) : List.of();
        int totalPaginas = (int) ((total + criterio.size() - 1) / criterio.size());
        return new PaginaFichasResponse(contenido, criterio.page(), criterio.size(), total, totalPaginas,
                criterio.modoDegradado(), criterio.filtrosIgnorados());
    }

    @Cacheable(cacheNames = CacheConfig.FICHAS, key = "#id.toString()", sync = true)
    public FichaPublicaResponse obtener(UUID id) {
        return repositorio.porId(id).orElseThrow(FichaNoEncontradaException::new);
    }
}
