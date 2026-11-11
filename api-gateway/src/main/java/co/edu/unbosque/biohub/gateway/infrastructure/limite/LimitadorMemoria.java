package co.edu.unbosque.biohub.gateway.infrastructure.limite;

import java.time.Duration;
import java.util.function.LongSupplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import co.edu.unbosque.biohub.gateway.domain.LimitadorPeticiones;
import co.edu.unbosque.biohub.gateway.domain.ResultadoLimite;

/**
 * Ventana fija en la memoria de esta instancia. Solo se usa si Redis no responde. El contador no es
 * compartido: con N instancias el límite real sería N veces mayor, y se acepta porque es temporal.
 * El tamaño está acotado para que muchas IP distintas no llenen la memoria.
 */
@Component
public class LimitadorMemoria implements LimitadorPeticiones {

    private static final int MAXIMO_CLAVES = 100_000;

    private record Ventana(long contador, long finMs) {
    }

    private final Cache<String, Ventana> ventanas = Caffeine.newBuilder()
            .maximumSize(MAXIMO_CLAVES)
            .expireAfterAccess(Duration.ofMinutes(10))
            .build();
    private final LongSupplier relojMs;

    @Autowired
    public LimitadorMemoria() {
        this(System::currentTimeMillis);
    }

    LimitadorMemoria(LongSupplier relojMs) {
        this.relojMs = relojMs;
    }

    @Override
    public ResultadoLimite consumir(String clave, int limite, Duration ventana) {
        long ahora = relojMs.getAsLong();
        Ventana actual = ventanas.asMap().compute(clave, (k, previa) -> {
            if (previa == null || previa.finMs() <= ahora) {
                return new Ventana(1, ahora + ventana.toMillis());
            }
            return new Ventana(previa.contador() + 1, previa.finMs());
        });
        return ResultadoLimite.de(actual.contador(), limite, Duration.ofMillis(actual.finMs() - ahora));
    }

    @Override
    public ResultadoLimite consultar(String clave, int limite, Duration ventana) {
        long ahora = relojMs.getAsLong();
        Ventana actual = ventanas.getIfPresent(clave);
        if (actual == null || actual.finMs() <= ahora) {
            return new ResultadoLimite(true, limite, limite, Duration.ZERO);
        }
        int restantes = (int) Math.max(0, limite - actual.contador());
        return new ResultadoLimite(restantes > 0, limite, restantes, Duration.ofMillis(actual.finMs() - ahora));
    }
}
