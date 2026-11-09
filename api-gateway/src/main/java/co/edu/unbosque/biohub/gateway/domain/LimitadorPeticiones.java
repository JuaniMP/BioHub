package co.edu.unbosque.biohub.gateway.domain;

import java.time.Duration;

/**
 * Cuenta peticiones por clave dentro de una ventana (Strategy): el filtro no sabe si el contador
 * vive en Redis o en memoria.
 */
public interface LimitadorPeticiones {

    /** Suma una petición a la clave y dice si sigue dentro del límite. */
    ResultadoLimite consumir(String clave, int limite, Duration ventana);

    /** Dice si la clave ya superó el límite, sin sumar nada. */
    ResultadoLimite consultar(String clave, int limite, Duration ventana);
}
