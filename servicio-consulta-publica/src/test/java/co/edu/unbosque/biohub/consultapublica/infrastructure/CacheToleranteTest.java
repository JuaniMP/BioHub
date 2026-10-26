package co.edu.unbosque.biohub.consultapublica.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import co.edu.unbosque.biohub.consultapublica.soporte.CacheRota;

class CacheToleranteTest {

    private final Cache rota = new CacheTolerante(new CacheRota(), new CacheErrorHandlerTolerante());

    @Test
    void conLaCacheCaidaLasLecturasSonMiss() {
        assertThat(rota.get("k")).isNull();
        assertThat(rota.get("k", String.class)).isNull();
    }

    @Test
    void conLaCacheCaidaLasEscriturasEInvalidacionesNoLanzan() {
        assertThatCode(() -> {
            rota.put("k", "v");
            rota.putIfAbsent("k", "v");
            rota.evict("k");
            rota.evictIfPresent("k");
            rota.clear();
            rota.invalidate();
        }).doesNotThrowAnyException();
    }

    @Test
    void conLaCacheCaidaLaCargaSeEjecutaYDevuelveElValor() {
        assertThat(rota.get("k", () -> "desde-bd")).isEqualTo("desde-bd");
    }

    @Test
    void conLaCacheCaidaUnErrorDeNegocioDeLaCargaSePropagaConSuCausaReal() {
        IllegalStateException negocio = new IllegalStateException("404 simulado");
        assertThatThrownBy(() -> rota.get("k", () -> {
            throw negocio;
        })).isInstanceOf(Cache.ValueRetrievalException.class).hasCause(negocio);
    }

    @Test
    void conLaCacheSanaFuncionaNormalYUnaCargaFallidaNoSeCachea() {
        Cache sana = new CacheTolerante(new ConcurrentMapCache("sana"), new CacheErrorHandlerTolerante());
        AtomicInteger cargas = new AtomicInteger();
        assertThat(sana.get("k", () -> "v" + cargas.incrementAndGet())).isEqualTo("v1");
        assertThat(sana.get("k", () -> "v" + cargas.incrementAndGet())).isEqualTo("v1");
        assertThat(cargas).hasValue(1);

        assertThatThrownBy(() -> sana.get("otra", () -> {
            throw new IllegalStateException("x");
        })).isInstanceOf(Cache.ValueRetrievalException.class);
        assertThat(sana.get("otra")).isNull();

        sana.evict("k");
        assertThat(sana.get("k")).isNull();
    }

    @Test
    void elManejadorAvisaComoMuchoUnaVezCada30Segundos() {
        long[] ahora = {1_000_000};
        CacheErrorHandlerTolerante h = new CacheErrorHandlerTolerante(() -> ahora[0]);
        var cache = new ConcurrentMapCache("c");
        // No lanza en ninguna operación (el conteo de logs no se expone; se valida que tolera ráfagas)
        for (int i = 0; i < 100; i++) {
            h.handleCacheGetError(new RuntimeException("x"), cache, "k");
            h.handleCachePutError(new RuntimeException("x"), cache, "k", "v");
            h.handleCacheEvictError(new RuntimeException("x"), cache, "k");
            h.handleCacheClearError(new RuntimeException("x"), cache);
            ahora[0] += 10_000;
        }
    }
}
