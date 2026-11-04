package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import org.springframework.security.core.Authentication;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.FilaHallazgo;

/**
 * Strategy de visibilidad de la ubicación (SAD §6.4.1, RF12, RF13, D41): decide qué se muestra de un hallazgo
 * según quién consulta. La obtiene {@link PoliticaAccesoUbicacion#estrategiaPara} y la usan la búsqueda interna
 * y el mapa, de modo que la regla vive en un solo punto. Un nivel de acceso nuevo es una estrategia nueva.
 */
public interface EstrategiaVisibilidadUbicacion {

    UbicacionResuelta resolver(Authentication autenticacion, FilaHallazgo fila);

    /**
     * {@code true} si la estrategia necesita el texto cifrado de la fila (solo la exacta). Si es {@code false}, el
     * texto cifrado ni siquiera se lee de la BD.
     */
    boolean necesitaCoordenadasCifradas();
}
