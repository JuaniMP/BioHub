package co.edu.unbosque.biohub.consultapublica.infrastructure;

import java.util.concurrent.Callable;

import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Decorador que hace tolerante a fallos cualquier {@link Cache} (D25, RNF1/RNF5): un error del almacén
 * (Redis caído, timeout) equivale a un "miss" y las escrituras/invalidaciones fallidas solo se registran.
 * <p>
 * Existe porque el manejo de errores de {@code @Cacheable(sync = true)} de Spring (6.2.x) pierde la causa real:
 * si la caché falla Y además la carga lanza una excepción de negocio (p. ej. 404), propaga la excepción de
 * Redis en lugar de la de negocio. Aquí la carga de respaldo se hace con la causa correcta.
 */
public class CacheTolerante implements Cache {

    private final Cache delegado;
    private final CacheErrorHandler errores;

    public CacheTolerante(Cache delegado, CacheErrorHandler errores) {
        this.delegado = delegado;
        this.errores = errores;
    }

    @Override
    public String getName() {
        return delegado.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegado.getNativeCache();
    }

    @Override
    public ValueWrapper get(Object key) {
        try {
            return delegado.get(key);
        } catch (RuntimeException e) {
            errores.handleCacheGetError(e, delegado, key);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Class<T> type) {
        try {
            return delegado.get(key, type);
        } catch (RuntimeException e) {
            errores.handleCacheGetError(e, delegado, key);
            return null;
        }
    }

    @Override
    public <T> T get(Object key, Callable<T> cargador) {
        try {
            return delegado.get(key, cargador);
        } catch (ValueRetrievalException fallaLaCarga) {
            throw fallaLaCarga; // el error es de la carga (negocio), no de la caché
        } catch (RuntimeException e) {
            errores.handleCacheGetError(e, delegado, key);
            try {
                return cargador.call();
            } catch (Exception causa) {
                throw new ValueRetrievalException(key, cargador, causa);
            }
        }
    }

    @Override
    public void put(Object key, Object value) {
        try {
            delegado.put(key, value);
        } catch (RuntimeException e) {
            errores.handleCachePutError(e, delegado, key, value);
        }
    }

    @Override
    public ValueWrapper putIfAbsent(Object key, Object value) {
        try {
            return delegado.putIfAbsent(key, value);
        } catch (RuntimeException e) {
            errores.handleCachePutError(e, delegado, key, value);
            return null;
        }
    }

    @Override
    public void evict(Object key) {
        try {
            delegado.evict(key);
        } catch (RuntimeException e) {
            errores.handleCacheEvictError(e, delegado, key);
        }
    }

    @Override
    public boolean evictIfPresent(Object key) {
        try {
            return delegado.evictIfPresent(key);
        } catch (RuntimeException e) {
            errores.handleCacheEvictError(e, delegado, key);
            return false;
        }
    }

    @Override
    public void clear() {
        try {
            delegado.clear();
        } catch (RuntimeException e) {
            errores.handleCacheClearError(e, delegado);
        }
    }

    @Override
    public boolean invalidate() {
        try {
            return delegado.invalidate();
        } catch (RuntimeException e) {
            errores.handleCacheClearError(e, delegado);
            return false;
        }
    }
}
