package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Puerto de persistencia del módulo ubicación (SAD §6.3.2, ARQ-10). Sobre esta interfaz se apilan los
 * decoradores: {@code DecoradorAuditoriaAcceso} -> {@code DecoradorCifradoCoordenadas} ->
 * {@code RepositorioUbicacionesJpa}. Los servicios reciben la cadena ya armada como una sola dependencia, así
 * que no pueden saltarse el cifrado ni la auditoría.
 *
 * <p>No tiene operaciones de eliminación ni de actualización de coordenadas: {@link #guardar} es el único
 * punto de escritura y las coordenadas en claro no pueden persistirse sin pasar por el decorador de cifrado.
 */
public interface RepositorioUbicaciones {

    boolean existe(UUID hallazgoId);

    /** Fila indexada para actualizar sus campos de búsqueda (nunca las coordenadas). */
    Optional<UbicacionHallazgo> porId(UUID hallazgoId);

    /**
     * Guarda la fila y la envía a la BD en la transacción en curso (flush).
     *
     * @param coordenadas coordenadas EN CLARO de un alta (en memoria, nunca se persisten así), o {@code null} si
     *                    no hay o si es una actualización del índice. El decorador de cifrado las consume; si
     *                    llegaran al componente JPA sin cifrar, este se niega a guardar.
     * @throws IllegalStateException si el cifrado falla (la transacción de registro se revierte)
     */
    void guardar(UbicacionHallazgo fila, Coordenadas coordenadas);

    long contar(CriterioBusquedaInterna criterio);

    /**
     * @param conCifrado si es {@code false} el texto cifrado ni siquiera sale de la BD (minimización)
     */
    List<FilaHallazgo> buscar(CriterioBusquedaInterna criterio, int offset, int limite, boolean conCifrado);

    /**
     * Descifra las coordenadas exactas de una fila leída antes con {@link #buscar} (conCifrado = true).
     * Se pasa el texto cifrado para no hacer una consulta por fila. Cada llamada queda en la auditoría.
     * La autorización por rol NO se decide aquí: la exige {@code ServicioDescifrado} antes de llamar (segunda barrera).
     *
     * @param usuario quien consulta (para la auditoría)
     * @throws CoordenadasNoDescifrablesException llave retirada o integridad fallida
     */
    Coordenadas obtenerUbicacionExacta(String usuario, UUID hallazgoId, CoordenadasCifradas cifradas);
}
