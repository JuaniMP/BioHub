package co.edu.unbosque.biohub.cientifico.registro.domain;

import co.edu.unbosque.biohub.cientifico.ubicacion.service.EstadoConservacion;

/**
 * Categoría de la Lista Roja de la UICN que informa el investigador, y su equivalencia con el
 * {@link EstadoConservacion} de 3 valores que acordamos con ubicación y con el portal público
 * (04-contrato-api §6, obligación 5 de registro).
 *
 * <p>La equivalencia la define registro, que es el dueño de la clasificación: ubicación solo reacciona.
 */
public enum CategoriaUicn {

    /** En peligro crítico. */
    CR(EstadoConservacion.EN_PELIGRO),
    /** En peligro. */
    EN(EstadoConservacion.EN_PELIGRO),
    /** Vulnerable. */
    VU(EstadoConservacion.NO_AMENAZADA),
    /** Casi amenazada. */
    NT(EstadoConservacion.NO_AMENAZADA),
    /** Preocupación menor. */
    LC(EstadoConservacion.NO_AMENAZADA),
    /** Datos insuficientes. */
    DD(EstadoConservacion.SIN_EVALUAR),
    /** No evaluada. */
    NE(EstadoConservacion.SIN_EVALUAR);

    private final EstadoConservacion estado;

    CategoriaUicn(EstadoConservacion estado) {
        this.estado = estado;
    }

    /** Estado de conservación que se publica y se envía a ubicación. */
    public EstadoConservacion estadoConservacion() {
        return estado;
    }
}
