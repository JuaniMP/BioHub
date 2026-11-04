package co.edu.unbosque.biohub.consultapublica.infrastructure;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.CacheErrorHandler;

/** Entrega cada caché envuelta en {@link CacheTolerante}. */
public class CacheManagerTolerante implements CacheManager {

    private final CacheManager delegado;
    private final CacheErrorHandler errores;
    private final ConcurrentMap<String, Cache> envueltas = new ConcurrentHashMap<>();

    public CacheManagerTolerante(CacheManager delegado, CacheErrorHandler errores) {
        this.delegado = delegado;
        this.errores = errores;
    }

    @Override
    public Cache getCache(String name) {
        Cache existente = envueltas.get(name);
        if (existente != null) {
            return existente;
        }
        Cache real = delegado.getCache(name);
        if (real == null) {
            return null;
        }
        return envueltas.computeIfAbsent(name, n -> new CacheTolerante(real, errores));
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegado.getCacheNames();
    }
}
