package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Lecturas sobre ubicacion_hallazgo (JPQL con parámetros enlazados). Las columnas coord_* solo se
 * seleccionan cuando {@code conCifrado} es true (minimización: para los demás roles el texto cifrado
 * ni siquiera sale de la BD).
 */
@Component
class ConsultorHallazgos {

    private static final String DESDE = " from UbicacionHallazgo h join Departamento d on d.codigo = h.departamentoCodigo"
            + " join d.region r where ";
    private static final String BASE = "select h.id, h.nombreCientifico, h.fechaRecoleccion, h.investigador,"
            + " h.estadoConservacion, d.nombre, d.codigo, r.nombre,"
            + " case when h.coordCifrada is not null then true else false end";
    private static final String CIFRADO = ", h.coordCifrada, h.coordNonce, h.claveVersion";
    private static final String ORDEN = " order by h.fechaRecoleccion desc, h.id";

    private final EntityManager em;

    ConsultorHallazgos(EntityManager em) {
        this.em = em;
    }

    long contar(CriterioBusquedaInterna criterio) {
        Query q = em.createQuery("select count(h)" + DESDE + criterio.clausulaWhere());
        criterio.enlazar(q);
        return ((Number) q.getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    List<FilaHallazgo> buscar(CriterioBusquedaInterna criterio, int offset, int limite, boolean conCifrado) {
        Query q = em.createQuery(BASE + (conCifrado ? CIFRADO : "") + DESDE + criterio.clausulaWhere() + ORDEN);
        criterio.enlazar(q);
        q.setFirstResult(offset);
        q.setMaxResults(limite);
        return ((List<Object[]>) q.getResultList()).stream().map(c -> mapear(c, conCifrado)).toList();
    }

    private static FilaHallazgo mapear(Object[] c, boolean conCifrado) {
        CoordenadasCifradas cifradas = conCifrado && c[9] != null
                ? new CoordenadasCifradas((byte[]) c[9], (byte[]) c[10], (String) c[11])
                : null;
        return new FilaHallazgo((UUID) c[0], (String) c[1], (LocalDate) c[2], (String) c[3],
                (EstadoConservacion) c[4], (String) c[5], (String) c[6], (String) c[7], (Boolean) c[8], cifradas);
    }
}
