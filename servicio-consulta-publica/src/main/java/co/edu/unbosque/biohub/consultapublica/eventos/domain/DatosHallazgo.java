package co.edu.unbosque.biohub.consultapublica.eventos.domain;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * {@code datos} de los eventos {@code hallazgo.*} (contrato 04, §7.3): snapshot completo del hallazgo.
 * Los campos extra no prohibidos se ignoran; los prohibidos (coordenadas) los rechaza antes
 * {@code ValidadorDatosHallazgo}. No incluye usuario ni investigador.
 */
public record DatosHallazgo(
        @NotNull UUID hallazgoId,
        @NotNull @Min(1) @Max(DatosHallazgo.VERSION_MAXIMA) Integer version,
        @NotBlank @Size(max = 150) String nombreCientifico,
        @Size(max = 150) String nombreComun,
        @NotBlank @Size(max = 100) String familia,
        @Size(max = 80) String tipoEspecimen,
        @NotNull LocalDate fechaRecoleccion,
        @NotBlank String estadoConservacion,
        @NotBlank @Size(max = 60) String departamento,
        @Size(max = 5000) String descripcion) {

    /**
     * Tope de cordura de {@code version} (SEC-J-01). Como gana la versión mayor, un valor desmesurado dejaría la ficha
     * "congelada": ningún evento legítimo posterior podría corregirla. 1.000.000 es muy superior a los cambios reales.
     */
    public static final int VERSION_MAXIMA = 1_000_000;
}
