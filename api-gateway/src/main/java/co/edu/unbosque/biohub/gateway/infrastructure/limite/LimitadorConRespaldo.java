package co.edu.unbosque.biohub.gateway.infrastructure.limite;

import java.time.Duration;
import java.util.function.Supplier;

import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.gateway.domain.LimitadorPeticiones;
import co.edu.unbosque.biohub.gateway.domain.ResultadoLimite;
import co.edu.unbosque.biohub.gateway.infrastructure.InterruptorRedis;

/**
 * Decorator: añade tolerancia a fallos al limitador de Redis sin modificarlo. Si Redis falla sigue
 * limitando en memoria: el portal no se cae por Redis (D25, RNF5) y tampoco queda sin límite (SEC-05).
 */
@Component
@Primary
public class LimitadorConRespaldo implements LimitadorPeticiones {

    private final LimitadorRedis redis;
    private final LimitadorMemoria memoria;
    private final InterruptorRedis interruptor;

    public LimitadorConRespaldo(LimitadorRedis redis, LimitadorMemoria memoria, InterruptorRedis interruptor) {
        this.redis = redis;
        this.memoria = memoria;
        this.interruptor = interruptor;
    }

    @Override
    public ResultadoLimite consumir(String clave, int limite, Duration ventana) {
        return intentar(() -> redis.consumir(clave, limite, ventana),
                () -> memoria.consumir(clave, limite, ventana));
    }

    @Override
    public ResultadoLimite consultar(String clave, int limite, Duration ventana) {
        return intentar(() -> redis.consultar(clave, limite, ventana),
                () -> memoria.consultar(clave, limite, ventana));
    }

    private ResultadoLimite intentar(Supplier<ResultadoLimite> principal, Supplier<ResultadoLimite> respaldo) {
        if (!interruptor.disponible()) {
            return respaldo.get();
        }
        try {
            return principal.get();
        } catch (DataAccessException e) {
            interruptor.registrarFallo(e);
            return respaldo.get();
        }
    }
}
