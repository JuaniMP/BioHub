package co.edu.unbosque.biohub.gateway.infrastructure.identidad;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.gateway.config.PropiedadesIdentidad;
import co.edu.unbosque.biohub.gateway.domain.RegistroRevocaciones;
import co.edu.unbosque.biohub.gateway.infrastructure.InterruptorRedis;

/**
 * Guarda la hora del último cambio de rol de cada usuario. La marca vive poco más que un token: después
 * de eso, todo token anterior al cambio ya venció por sí solo.
 *
 * <p>Si Redis no responde se deja pasar el token. Es una decisión consciente: el peor caso es el de antes
 * (el cambio se nota al vencer el token, 5 minutos como máximo), mientras que rechazar dejaría a todo el
 * personal interno sin servicio por una caída de Redis.
 */
@Component
public class RevocacionesRedis implements RegistroRevocaciones {

    private static final String PREFIJO = "biohub:gateway:revocado:";
    private static final Duration MARGEN = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final InterruptorRedis interruptor;
    private final Duration vida;
    private final Clock reloj;

    public RevocacionesRedis(StringRedisTemplate redis, InterruptorRedis interruptor, PropiedadesIdentidad identidad,
                             Clock reloj) {
        this.redis = redis;
        this.interruptor = interruptor;
        this.vida = identidad.vidaToken().plus(MARGEN);
        this.reloj = reloj;
    }

    @Override
    public void marcar(String usuarioId) {
        try {
            redis.opsForValue().set(PREFIJO + usuarioId, String.valueOf(reloj.instant().getEpochSecond()), vida);
        } catch (DataAccessException e) {
            interruptor.registrarFallo(e);
        }
    }

    @Override
    public boolean revocado(String usuarioId, Instant emitidoEn) {
        if (!interruptor.disponible()) {
            return false;
        }
        try {
            String marca = redis.opsForValue().get(PREFIJO + usuarioId);
            return marca != null && emitidoEn.getEpochSecond() <= Long.parseLong(marca);
        } catch (DataAccessException e) {
            interruptor.registrarFallo(e);
            return false;
        }
    }
}
