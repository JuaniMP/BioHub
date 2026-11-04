package co.edu.unbosque.biohub.consultapublica.soporte;

import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.data.redis.RedisSystemException;

/** Caché que simula Redis caído: todo falla. */
public class CacheRota extends ConcurrentMapCache {
    public CacheRota() {
        super("rota");
    }

    private static RuntimeException caida() {
        return new RedisSystemException("Redis caído", new RuntimeException("no conectado"));
    }

    @Override
    public ValueWrapper get(Object key) {
        throw caida();
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        throw caida();
    }

    @Override
    public <T> T get(Object key, java.util.concurrent.Callable<T> valueLoader) {
        throw caida();
    }

    @Override
    public void put(Object key, Object value) {
        throw caida();
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        throw caida();
    }

    @Override
    public void evict(Object key) {
        throw caida();
    }

    @Override
    public boolean evictIfPresent(Object key) {
        throw caida();
    }

    @Override
    public void clear() {
        throw caida();
    }

    @Override
    public boolean invalidate() {
        throw caida();
    }
}
