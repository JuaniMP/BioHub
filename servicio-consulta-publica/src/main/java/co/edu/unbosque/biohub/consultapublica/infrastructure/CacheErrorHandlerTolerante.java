package co.edu.unbosque.biohub.consultapublica.infrastructure;

import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Redis no es fuente de verdad ni punto único de fallo (D25, RNF1/RNF5): cualquier error de caché se
 * convierte en "miss" (o se omite) y se registra un WARN, como mucho uno cada 30 s para no inundar el log.
 */
public class CacheErrorHandlerTolerante implements CacheErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(CacheErrorHandlerTolerante.class);
    static final long INTERVALO_LOG_MS = 30_000;

    private final AtomicLong ultimoAviso = new AtomicLong(0);
    private final java.util.function.LongSupplier reloj;

    public CacheErrorHandlerTolerante() {
        this(System::currentTimeMillis);
    }

    CacheErrorHandlerTolerante(java.util.function.LongSupplier reloj) {
        this.reloj = reloj;
    }

    @Override
    public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
        avisar("get", cache, e);
    }

    @Override
    public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
        avisar("put", cache, e);
    }

    @Override
    public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
        avisar("evict", cache, e);
    }

    @Override
    public void handleCacheClearError(RuntimeException e, Cache cache) {
        avisar("clear", cache, e);
    }

    private void avisar(String operacion, Cache cache, RuntimeException e) {
        long ahora = reloj.getAsLong();
        long previo = ultimoAviso.get();
        if (ahora - previo >= INTERVALO_LOG_MS && ultimoAviso.compareAndSet(previo, ahora)) {
            log.warn("Caché no disponible (operación={} caché={}): se atiende desde MySQL. causa={}",
                    operacion, cache.getName(), e.getClass().getSimpleName());
        }
    }
}
