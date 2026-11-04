package co.edu.unbosque.biohub.consultapublica.service;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.consultapublica.config.CacheConfig;

/**
 * Invalida las cachés tras aplicar un evento: {@code fichas::<id>} y todas las {@code busquedas}.
 * Si Redis falla solo se registra un WARN: el dato viejo vive como máximo un TTL (riesgo aceptado, D25) y
 * el evento NO debe fallar por eso.
 */
@Component
public class InvalidadorCacheFichas {

    private static final Logger log = LoggerFactory.getLogger(InvalidadorCacheFichas.class);

    private final CacheManager cacheManager;

    public InvalidadorCacheFichas(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    public void invalidar(UUID hallazgoId) {
        try {
            Cache fichas = cacheManager.getCache(CacheConfig.FICHAS);
            if (fichas != null) {
                fichas.evict(hallazgoId.toString());
            }
            Cache busquedas = cacheManager.getCache(CacheConfig.BUSQUEDAS);
            if (busquedas != null) {
                busquedas.clear();
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo invalidar la caché hallazgoId={} causa={}", hallazgoId, e.getClass().getSimpleName());
        }
    }
}
