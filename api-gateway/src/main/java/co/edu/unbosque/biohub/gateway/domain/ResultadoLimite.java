package co.edu.unbosque.biohub.gateway.domain;

import java.time.Duration;

/**
 * Resultado de contar una petición.
 *
 * @param permitido     si la petición puede continuar
 * @param limite        máximo de la ventana
 * @param restantes     peticiones que quedan en la ventana
 * @param reintentarEn  tiempo hasta que la ventana se reinicia
 */
public record ResultadoLimite(boolean permitido, int limite, int restantes, Duration reintentarEn) {

    public static ResultadoLimite de(long contador, int limite, Duration reintentarEn) {
        int restantes = (int) Math.max(0, limite - contador);
        return new ResultadoLimite(contador <= limite, limite, restantes, reintentarEn);
    }

    /** Segundos para la cabecera Retry-After: redondea hacia arriba y nunca devuelve 0. */
    public long segundosParaReintentar() {
        long milisegundos = reintentarEn.toMillis();
        return Math.max(1, (milisegundos + 999) / 1000);
    }
}
