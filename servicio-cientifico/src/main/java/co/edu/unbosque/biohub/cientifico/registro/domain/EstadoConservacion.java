package co.edu.unbosque.biohub.cientifico.registro.domain;

/**
 * Estado de conservación de un hallazgo, en los tres valores acordados con el portal público y con
 * ubicación (04-contrato-api §6 y §7.3).
 *
 * <p>Es un enum propio de registro y no el de ubicación: según D53 este módulo no importa nada del
 * módulo de ubicación, y la integración entre ambos ocurre solo por los eventos de dominio,
 * donde el estado viaja como texto. Son dos declaraciones del mismo acuerdo, cada una en su módulo;
 * {@link CategoriaUicn} fija la equivalencia y hay pruebas que la sostienen.
 */
public enum EstadoConservacion {

    /** Especie amenazada: ubicación cifra sus coordenadas y el portal nunca las muestra. */
    EN_PELIGRO,
    /** Especie sin amenaza registrada. */
    NO_AMENAZADA,
    /** Todavía sin evaluar. */
    SIN_EVALUAR
}
