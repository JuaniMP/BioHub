package co.edu.unbosque.biohub.cientifico.ubicacion.service;

/** Resultado de {@link UbicacionService#registrar} y {@link UbicacionService#actualizar}. */
public enum ResultadoUbicacion {
    APLICADA, IGNORADA_DUPLICADA, IGNORADA_VERSION_ANTIGUA
}
