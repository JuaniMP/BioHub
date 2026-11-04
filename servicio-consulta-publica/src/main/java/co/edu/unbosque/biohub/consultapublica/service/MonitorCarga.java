package co.edu.unbosque.biohub.consultapublica.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.consultapublica.config.PropiedadesPublica;

/**
 * Monitor de carga de la consulta pública (ARQ-06, ASR1, RNF1). Mide las peticiones en curso y el p95 de la
 * duración en una ventana deslizante, y decide si la búsqueda debe degradarse ({@link #degradado()}).
 *
 * <p><b>Histéresis</b> para no oscilar: se entra con CUALQUIERA de los umbrales de entrada (peticiones en curso o
 * p95) y se sale solo cuando las dos señales están por debajo de los umbrales de salida, que son más bajos, y
 * ya pasó la permanencia mínima en modo degradado. Si {@code biohub.publica.modo-degradado=true} la búsqueda
 * queda degradada siempre (forzado manual) y el monitor solo informa.
 *
 * <p>Costo por petición: dos operaciones atómicas y una escritura en un buffer circular acotado
 * ({@value #CAPACIDAD} muestras: bajo un pico enorme la ventana conserva las más recientes). El p95 se recalcula
 * como mucho una vez por {@code intervaloEvaluacion}; la entrada por peticiones en curso es inmediata.
 */
@Component
public class MonitorCarga {

    private static final Logger log = LoggerFactory.getLogger(MonitorCarga.class);
    static final int CAPACIDAD = 4096;

    /** Quién decidió el estado actual. */
    public enum Origen { FORZADO, AUTOMATICO, NINGUNO }

    /** Foto del monitor para actuator. */
    public record Estado(boolean degradado, Origen origen, int enCurso, long p95Ms, int muestras, Instant desde,
                         long cambios) {
    }

    private final PropiedadesPublica.Degradacion cfg;
    private final boolean forzado;
    private final Clock reloj;

    private final AtomicInteger enCurso = new AtomicInteger();
    private final AtomicLong ultimaEvaluacion = new AtomicLong(Long.MIN_VALUE);
    private final AtomicLong cambios = new AtomicLong();

    private final Object cerrojo = new Object();
    private final long[] instantes = new long[CAPACIDAD];
    private final int[] duraciones = new int[CAPACIDAD];
    private int siguiente;
    private int cuenta;

    private volatile boolean degradado;
    private volatile long desdeMs;
    private volatile long p95Ms;
    private volatile int muestras;

    @Autowired
    public MonitorCarga(PropiedadesPublica propiedades) {
        this(propiedades, Clock.systemUTC());
    }

    MonitorCarga(PropiedadesPublica propiedades, Clock reloj) {
        this.cfg = propiedades.degradacion();
        this.forzado = propiedades.modoDegradado();
        this.reloj = reloj;
        this.desdeMs = reloj.millis();
        if (forzado) {
            log.warn("Búsqueda pública FORZADA a modo degradado (CONSULTA_MODO_DEGRADADO=true)");
        }
    }

    /** Una petición de lectura empieza. Puede degradar de inmediato si ya hay demasiadas en curso. */
    public void iniciarPeticion() {
        int n = enCurso.incrementAndGet();
        if (cfg.automatica() && !forzado && !degradado && n >= cfg.enCursoEntrada()) {
            cambiar(true, reloj.millis(), "peticiones en curso=" + n);
        }
    }

    /** La petición terminó (bien o mal) tras {@code duracionMs}. */
    public void terminarPeticion(long duracionMs) {
        enCurso.decrementAndGet();
        long ahora = reloj.millis();
        synchronized (cerrojo) {
            instantes[siguiente] = ahora;
            duraciones[siguiente] = (int) Math.min(Math.max(duracionMs, 0), Integer.MAX_VALUE);
            siguiente = (siguiente + 1) % CAPACIDAD;
            cuenta = Math.min(cuenta + 1, CAPACIDAD);
        }
    }

    /** {@code true} si la próxima búsqueda debe ser la degradada. */
    public boolean degradado() {
        if (forzado) {
            return true;
        }
        if (!cfg.automatica()) {
            return false;
        }
        evaluarSiToca(reloj.millis());
        return degradado;
    }

    public Estado estado() {
        if (!forzado && cfg.automatica()) {
            evaluarSiToca(reloj.millis());
        }
        boolean d = forzado || degradado;
        Origen origen = forzado ? Origen.FORZADO : d ? Origen.AUTOMATICO : Origen.NINGUNO;
        return new Estado(d, origen, enCurso.get(), p95Ms, muestras, Instant.ofEpochMilli(desdeMs), cambios.get());
    }

    private void evaluarSiToca(long ahora) {
        long ultima = ultimaEvaluacion.get();
        if (ultima != Long.MIN_VALUE && ahora - ultima < cfg.intervaloEvaluacion().toMillis()) {
            return;
        }
        if (!ultimaEvaluacion.compareAndSet(ultima, ahora)) {
            return; // otro hilo está evaluando
        }
        long[] ventana = duracionesEnVentana(ahora);
        long p95 = percentil95(ventana);
        p95Ms = p95;
        muestras = ventana.length;
        boolean suficientes = ventana.length >= cfg.muestrasMinimas();
        int n = enCurso.get();
        if (!degradado) {
            if (n >= cfg.enCursoEntrada()) {
                cambiar(true, ahora, "peticiones en curso=" + n);
            } else if (suficientes && p95 >= cfg.p95EntradaMs()) {
                cambiar(true, ahora, "p95=" + p95 + " ms");
            }
        } else if (ahora - desdeMs >= cfg.permanenciaMinima().toMillis()
                && n <= cfg.enCursoSalida() && (!suficientes || p95 <= cfg.p95SalidaMs())) {
            cambiar(false, ahora, "carga normalizada (en curso=" + n + ", p95=" + p95 + " ms)");
        }
    }

    private long[] duracionesEnVentana(long ahora) {
        long desde = ahora - cfg.ventana().toMillis();
        long[] copia = new long[CAPACIDAD];
        int k = 0;
        synchronized (cerrojo) {
            for (int i = 0; i < cuenta; i++) {
                if (instantes[i] >= desde) {
                    copia[k++] = duraciones[i];
                }
            }
        }
        return Arrays.copyOf(copia, k);
    }

    /** Percentil 95 por rango más cercano; 0 si no hay muestras. */
    static long percentil95(long[] valores) {
        if (valores.length == 0) {
            return 0;
        }
        long[] orden = valores.clone();
        Arrays.sort(orden);
        int indice = (int) Math.ceil(0.95 * orden.length) - 1;
        return orden[Math.max(indice, 0)];
    }

    private void cambiar(boolean nuevo, long ahora, String motivo) {
        synchronized (cerrojo) {
            if (degradado == nuevo) {
                return;
            }
            degradado = nuevo;
            desdeMs = ahora;
            cambios.incrementAndGet();
        }
        if (nuevo) {
            log.warn("Búsqueda pública pasa a DEGRADADA ({})", motivo);
        } else {
            log.info("Búsqueda pública vuelve a COMPLETA ({})", motivo);
        }
    }
}
