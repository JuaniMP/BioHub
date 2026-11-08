package co.edu.unbosque.biohub.gateway.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.gateway.config.PropiedadesCircuito;

/**
 * Circuit Breaker por servicio destino (SAD §5.1, ARQ-20). Sin él, con un servicio colgado cada
 * petición esperaría el timeout (hasta 30 s) ocupando un hilo del gateway. Con el circuito abierto
 * responde 503 al instante, y así un servicio caído no arrastra a los demás.
 *
 * <ul>
 *   <li><b>Cerrado:</b> todo pasa. Si hay {@code fallosParaAbrir} fallos de conexión seguidos, se abre.</li>
 *   <li><b>Abierto:</b> rechaza durante {@code tiempoAbierto}.</li>
 *   <li><b>Medio abierto:</b> deja pasar una sola petición de prueba. Si sale bien se cierra; si falla,
 *       se vuelve a abrir.</li>
 * </ul>
 *
 * <p>Solo cuentan los fallos de conexión o de tiempo. Un 4xx o 5xx es una respuesta del servicio, no una
 * caída: por ejemplo, el 503 de consulta-publica con su base caída no debe cortar las fichas en caché.
 */
@Component
public class CircuitoServicios {

    private static final Logger log = LoggerFactory.getLogger(CircuitoServicios.class);

    private enum Fase { CERRADO, ABIERTO, MEDIO_ABIERTO }

    private static final class Estado {
        private Fase fase = Fase.CERRADO;
        private int fallosSeguidos;
        private long abiertoHastaMs;
        private boolean pruebaEnCurso;
    }

    private final Map<String, Estado> estados = new ConcurrentHashMap<>();
    private final int fallosParaAbrir;
    private final long tiempoAbiertoMs;
    private final LongSupplier relojMs;

    @Autowired
    public CircuitoServicios(PropiedadesCircuito propiedades) {
        this(propiedades, System::currentTimeMillis);
    }

    CircuitoServicios(PropiedadesCircuito propiedades, LongSupplier relojMs) {
        this.fallosParaAbrir = propiedades.fallosParaAbrir();
        this.tiempoAbiertoMs = propiedades.tiempoAbierto().toMillis();
        this.relojMs = relojMs;
    }

    /** Lanza CircuitoAbiertoException si no se debe intentar la conexión. */
    public void permitir(String destino) {
        Estado estado = estados.computeIfAbsent(destino, d -> new Estado());
        synchronized (estado) {
            long ahora = relojMs.getAsLong();
            if (estado.fase == Fase.ABIERTO && ahora >= estado.abiertoHastaMs) {
                estado.fase = Fase.MEDIO_ABIERTO;
                estado.pruebaEnCurso = false;
            }
            if (estado.fase == Fase.ABIERTO) {
                throw new CircuitoAbiertoException(segundos(estado.abiertoHastaMs - ahora));
            }
            if (estado.fase == Fase.MEDIO_ABIERTO) {
                if (estado.pruebaEnCurso) {
                    throw new CircuitoAbiertoException(1);
                }
                estado.pruebaEnCurso = true;
            }
        }
    }

    public void registrarExito(String destino) {
        Estado estado = estados.computeIfAbsent(destino, d -> new Estado());
        synchronized (estado) {
            if (estado.fase != Fase.CERRADO) {
                log.info("Circuito cerrado: {} responde de nuevo", destino);
            }
            estado.fase = Fase.CERRADO;
            estado.fallosSeguidos = 0;
            estado.pruebaEnCurso = false;
        }
    }

    public void registrarFallo(String destino) {
        Estado estado = estados.computeIfAbsent(destino, d -> new Estado());
        synchronized (estado) {
            estado.fallosSeguidos++;
            if (estado.fase == Fase.MEDIO_ABIERTO || estado.fallosSeguidos >= fallosParaAbrir) {
                if (estado.fase != Fase.ABIERTO) {
                    log.warn("Circuito abierto: {} no responde; se rechaza durante {} ms", destino, tiempoAbiertoMs);
                }
                estado.fase = Fase.ABIERTO;
                estado.abiertoHastaMs = relojMs.getAsLong() + tiempoAbiertoMs;
                estado.pruebaEnCurso = false;
            }
        }
    }

    /** La petición terminó por otra causa (ni éxito ni caída): libera la prueba sin decidir. */
    public void liberar(String destino) {
        Estado estado = estados.get(destino);
        if (estado != null) {
            synchronized (estado) {
                estado.pruebaEnCurso = false;
            }
        }
    }

    private static long segundos(long milisegundos) {
        return Math.max(1, (milisegundos + 999) / 1000);
    }
}
