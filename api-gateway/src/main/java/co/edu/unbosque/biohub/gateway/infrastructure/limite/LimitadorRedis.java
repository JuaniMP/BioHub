package co.edu.unbosque.biohub.gateway.infrastructure.limite;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.gateway.domain.LimitadorPeticiones;
import co.edu.unbosque.biohub.gateway.domain.ResultadoLimite;

/**
 * Ventana fija en Redis: el contador es compartido por todas las instancias del gateway.
 *
 * <p>Los scripts Lua se ejecutan de forma atómica. Es lo que evita dos fallos: que dos peticiones
 * simultáneas lean el mismo número, y que el proceso muera entre sumar y poner el vencimiento,
 * lo que dejaría el contador vivo para siempre y al consumidor bloqueado.
 */
@Component
public class LimitadorRedis implements LimitadorPeticiones {

    static final String PREFIJO = "biohub:gateway:rl:";

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SUMAR = new DefaultRedisScript<>("""
            local contador = redis.call('INCR', KEYS[1])
            if contador == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            local restante = redis.call('PTTL', KEYS[1])
            if restante < 0 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
              restante = tonumber(ARGV[1])
            end
            return {contador, restante}
            """, List.class);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> LEER = new DefaultRedisScript<>("""
            local contador = tonumber(redis.call('GET', KEYS[1]) or '0')
            local restante = redis.call('PTTL', KEYS[1])
            return {contador, restante}
            """, List.class);

    private final StringRedisTemplate redis;

    public LimitadorRedis(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public ResultadoLimite consumir(String clave, int limite, Duration ventana) {
        return resultado(redis.execute(SUMAR, List.of(PREFIJO + clave), String.valueOf(ventana.toMillis())), limite);
    }

    @Override
    public ResultadoLimite consultar(String clave, int limite, Duration ventana) {
        ResultadoLimite actual = resultado(redis.execute(LEER, List.of(PREFIJO + clave)), limite);
        // Consultar no consume: se permite si todavía queda al menos un intento.
        return new ResultadoLimite(actual.restantes() > 0, limite, actual.restantes(), actual.reintentarEn());
    }

    private static ResultadoLimite resultado(List<?> respuesta, int limite) {
        long contador = ((Number) respuesta.get(0)).longValue();
        long restanteMs = Math.max(0, ((Number) respuesta.get(1)).longValue());
        return ResultadoLimite.de(contador, limite, Duration.ofMillis(restanteMs));
    }
}
