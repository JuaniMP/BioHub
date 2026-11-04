package co.edu.unbosque.biohub.consultapublica.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuración de la consulta pública ({@code biohub.publica.*}).
 *
 * @param modoDegradado FORZADO manual (RNF1, {@code CONSULTA_MODO_DEGRADADO}): si es {@code true} siempre se usa la
 *                      búsqueda degradada, sin importar la carga. Si es {@code false} decide el monitor de carga
 * @param degradacion   umbrales de la degradación automática (ARQ-06, ASR1); todos tienen valor por defecto
 */
@ConfigurationProperties("biohub.publica")
public record PropiedadesPublica(@DefaultValue("false") boolean modoDegradado,
                                 @DefaultValue Degradacion degradacion) {

    /**
     * Degradación automática con histéresis. Se entra con CUALQUIERA de los umbrales de entrada y se sale solo
     * cuando TODAS las señales están por debajo de los de salida (más bajos) y pasó la permanencia mínima.
     *
     * @param automatica        activa la degradación según la carga
     * @param enCursoEntrada    peticiones simultáneas a partir de las cuales se degrada
     * @param enCursoSalida     peticiones simultáneas por debajo de las cuales se puede volver a la búsqueda completa
     * @param p95EntradaMs      p95 de la ventana (ms) a partir del cual se degrada (RNF5 pide ≤ 2 s)
     * @param p95SalidaMs       p95 (ms) por debajo del cual se puede volver
     * @param ventana           ventana deslizante sobre la que se calcula el p95
     * @param muestrasMinimas   con menos muestras en la ventana no se decide por p95 (evita decidir con ruido)
     * @param permanenciaMinima tiempo mínimo en modo degradado antes de evaluar la salida (evita oscilar)
     * @param intervaloEvaluacion cada cuánto se recalcula el p95 (la entrada por peticiones en curso es inmediata)
     */
    public record Degradacion(
            @DefaultValue("true") boolean automatica,
            @DefaultValue("100") int enCursoEntrada,
            @DefaultValue("50") int enCursoSalida,
            @DefaultValue("1000") long p95EntradaMs,
            @DefaultValue("500") long p95SalidaMs,
            @DefaultValue("10s") Duration ventana,
            @DefaultValue("20") int muestrasMinimas,
            @DefaultValue("15s") Duration permanenciaMinima,
            @DefaultValue("1s") Duration intervaloEvaluacion) {

        /** Valores por defecto (los mismos que {@code @DefaultValue}); útil para pruebas. */
        public static Degradacion porDefecto() {
            return new Degradacion(true, 100, 50, 1000, 500, Duration.ofSeconds(10), 20, Duration.ofSeconds(15),
                    Duration.ofSeconds(1));
        }

        public Degradacion {
            if (enCursoEntrada < 1 || enCursoSalida < 0 || enCursoSalida >= enCursoEntrada) {
                throw new IllegalArgumentException("biohub.publica.degradacion: en-curso-salida debe ser menor que en-curso-entrada");
            }
            if (p95EntradaMs < 1 || p95SalidaMs < 0 || p95SalidaMs >= p95EntradaMs) {
                throw new IllegalArgumentException("biohub.publica.degradacion: p95-salida-ms debe ser menor que p95-entrada-ms");
            }
            if (ventana == null || ventana.isNegative() || ventana.isZero()
                    || permanenciaMinima == null || permanenciaMinima.isNegative()
                    || intervaloEvaluacion == null || intervaloEvaluacion.isNegative()) {
                throw new IllegalArgumentException("biohub.publica.degradacion: ventana, permanencia e intervalo deben ser duraciones válidas");
            }
            if (muestrasMinimas < 1) {
                throw new IllegalArgumentException("biohub.publica.degradacion: muestras-minimas debe ser al menos 1");
            }
        }
    }
}
