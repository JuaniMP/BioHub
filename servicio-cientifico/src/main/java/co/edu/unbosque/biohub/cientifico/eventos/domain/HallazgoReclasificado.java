package co.edu.unbosque.biohub.cientifico.eventos.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Se reclasificó o editó un hallazgo (cambio de estado de conservación, taxonomía, fecha o departamento). Se
 * publica igual que {@link HallazgoRegistrado}: con {@code ApplicationEventPublisher}, dentro de la transacción
 * de registro y después de guardar la nueva versión. No lleva coordenadas: corregirlas no está en el contrato.
 *
 * @param version            versión NUEVA del hallazgo; si no es mayor que la guardada, ubicacion la ignora
 * @param estadoConservacion {@code EN_PELIGRO}, {@code NO_AMENAZADA} o {@code SIN_EVALUAR}
 * @param departamento       código DANE ("15") o nombre oficial ("Boyacá")
 */
public record HallazgoReclasificado(
        UUID hallazgoId,
        int version,
        String nombreCientifico,
        LocalDate fechaRecoleccion,
        String departamento,
        String estadoConservacion,
        Instant ocurridoEn) implements EventoDominio {
}
