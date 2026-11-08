package co.edu.unbosque.biohub.gateway.infrastructure;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Tras un fallo de Redis, deja de intentarlo durante unos segundos. Sin esto, con Redis caído cada
 * petición perdería hasta 300 ms esperando el timeout antes de usar el respaldo.
 */
@Component
public class InterruptorRedis {

    private static final Logger log = LoggerFactory.getLogger(InterruptorRedis.class);
    private static final long ESPERA_MS = 5_000;
    private static final long INTERVALO_AVISO_MS = 30_000;

    private final LongSupplier relojMs;
    private final AtomicLong reintentarDesde = new AtomicLong();
    private final AtomicLong ultimoAviso = new AtomicLong();

    @Autowired
    public InterruptorRedis() {
        this(System::currentTimeMillis);
    }

    InterruptorRedis(LongSupplier relojMs) {
        this.relojMs = relojMs;
    }

    public boolean disponible() {
        return relojMs.getAsLong() >= reintentarDesde.get();
    }

    public void registrarFallo(Exception causa) {
        long ahora = relojMs.getAsLong();
        reintentarDesde.set(ahora + ESPERA_MS);
        long anterior = ultimoAviso.get();
        if (ahora - anterior >= INTERVALO_AVISO_MS && ultimoAviso.compareAndSet(anterior, ahora)) {
            log.warn("Redis no responde; se usa el respaldo ({})", causa.getClass().getSimpleName());
        }
    }
}
