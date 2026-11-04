package co.edu.unbosque.biohub.cientifico.eventos.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Se registró un hallazgo nuevo. Cómo publicarlo (registro, Hernando): dentro de la misma transacción en que
 * inserta el hallazgo y DESPUÉS de insertarlo:
 *
 * <pre>{@code
 * publicador.publishEvent(new HallazgoRegistrado(id, 1, nombre, fecha, investigador,
 *         departamento, "EN_PELIGRO", latitud, longitud, Instant.now()));
 * }</pre>
 *
 * El listener de ubicacion se ejecuta síncrono, en esa transacción: si falla (datos inválidos o error de
 * cifrado) lanza la excepción, publishEvent la propaga y se revierte todo el registro. Sin transacción activa
 * falla con IllegalTransactionStateException (propagation MANDATORY de {@code UbicacionService}).
 *
 * <p>Las coordenadas viajan SOLO en memoria, dentro de este evento en proceso, y solo para que ubicacion las cifre:
 * no las copies a logs, historial ni al {@code EventoSobre} del broker (D14, RNF9). {@code toString()} las enmascara.
 * Ambas o ninguna ({@code null} = hallazgo sin coordenadas).
 *
 * @param estadoConservacion {@code EN_PELIGRO}, {@code NO_AMENAZADA} o {@code SIN_EVALUAR} (como texto para no
 *                           acoplar este paquete al módulo ubicacion)
 * @param departamento       código DANE ("15") o nombre oficial ("Boyacá")
 * @param investigador       preferred_username de quien registra
 */
public record HallazgoRegistrado(
        UUID hallazgoId,
        int version,
        String nombreCientifico,
        LocalDate fechaRecoleccion,
        String investigador,
        String departamento,
        String estadoConservacion,
        Double latitud,
        Double longitud,
        Instant ocurridoEn) implements EventoDominio {

    @Override
    public String toString() {
        return "HallazgoRegistrado[hallazgoId=" + hallazgoId + ", version=" + version + ", coordenadas=***]";
    }
}
