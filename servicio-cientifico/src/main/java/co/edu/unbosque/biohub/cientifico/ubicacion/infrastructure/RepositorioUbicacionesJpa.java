package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CriterioBusquedaInterna;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.FilaHallazgo;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgo;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgoRepository;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.EstadoConservacion;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Componente concreto del Decorator (SAD §6.3.2): solo accede a MySQL. No cifra, no descifra y no audita: eso lo
 * agregan los decoradores. Lecturas en JPQL con parámetros enlazados. Las columnas {@code coord_*} solo se
 * seleccionan si {@code conCifrado} es true (minimización: para los demás roles el texto cifrado ni sale de la BD).
 * Es privada del paquete: solo {@link ConfiguracionUbicacion} la instancia (D43, SEC-J-10).
 */
final class RepositorioUbicacionesJpa implements RepositorioUbicaciones {

    private static final String DESDE = " from UbicacionHallazgo h join Departamento d on d.codigo = h.departamentoCodigo"
            + " join d.region r where ";
    private static final String BASE = "select h.id, h.nombreCientifico, h.fechaRecoleccion, h.investigador,"
            + " h.estadoConservacion, d.nombre, d.codigo, r.nombre,"
            + " case when h.coordCifrada is not null then true else false end";
    private static final String CIFRADO = ", h.coordCifrada, h.coordNonce, h.claveVersion";
    private static final String ORDEN = " order by h.fechaRecoleccion desc, h.id";

    private final UbicacionHallazgoRepository repositorio;
    private final EntityManager em;

    RepositorioUbicacionesJpa(UbicacionHallazgoRepository repositorio, EntityManager em) {
        this.repositorio = repositorio;
        this.em = em;
    }

    @Override
    public boolean existe(UUID hallazgoId) {
        return repositorio.existsById(hallazgoId);
    }

    @Override
    public Optional<UbicacionHallazgo> porId(UUID hallazgoId) {
        return repositorio.findById(hallazgoId);
    }

    @Override
    public void guardar(UbicacionHallazgo fila, Coordenadas coordenadas) {
        if (coordenadas != null) {
            // Red de seguridad: unas coordenadas en claro solo pueden llegar aquí si alguien armó la cadena sin el
            // decorador de cifrado. No hay columnas para guardarlas y se rechaza antes de tocar la BD.
            throw new IllegalStateException("Coordenadas sin cifrar: falta DecoradorCifradoCoordenadas en la cadena");
        }
        repositorio.saveAndFlush(fila);
    }

    @Override
    public long contar(CriterioBusquedaInterna criterio) {
        Query q = em.createQuery("select count(h)" + DESDE + criterio.clausulaWhere());
        criterio.enlazar(q);
        return ((Number) q.getSingleResult()).longValue();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<FilaHallazgo> buscar(CriterioBusquedaInterna criterio, int offset, int limite, boolean conCifrado) {
        Query q = em.createQuery(BASE + (conCifrado ? CIFRADO : "") + DESDE + criterio.clausulaWhere() + ORDEN);
        criterio.enlazar(q);
        q.setFirstResult(offset);
        q.setMaxResults(limite);
        return ((List<Object[]>) q.getResultList()).stream().map(c -> mapear(c, conCifrado)).toList();
    }

    @Override
    public Coordenadas obtenerUbicacionExacta(String usuario, UUID hallazgoId, CoordenadasCifradas cifradas) {
        // Este componente no conoce las llaves: descifrar es responsabilidad de DecoradorCifradoCoordenadas
        throw new UnsupportedOperationException("El descifrado lo hace DecoradorCifradoCoordenadas");
    }

    private static FilaHallazgo mapear(Object[] c, boolean conCifrado) {
        CoordenadasCifradas cifradas = conCifrado && c[9] != null
                ? new CoordenadasCifradas((byte[]) c[9], (byte[]) c[10], (String) c[11])
                : null;
        return new FilaHallazgo((UUID) c[0], (String) c[1], (LocalDate) c[2], (String) c[3],
                (EstadoConservacion) c[4], (String) c[5], (String) c[6], (String) c[7], (Boolean) c[8], cifradas);
    }
}
