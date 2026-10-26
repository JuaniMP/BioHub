package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.nio.ByteBuffer;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import co.edu.unbosque.biohub.cientifico.ubicacion.controller.RecifradoResponse;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.ProveedorDeLlaves;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * Re-cifrado tras rotar la llave (05 §12): lotes de 500 de filas cuya {@code clave_version} no es la activa,
 * paginación por clave, {@code FOR UPDATE SKIP LOCKED}, una transacción por lote y nonce nuevo por fila.
 * Idempotente y reanudable. El texto plano no sale del cifrador.
 */
@Service
public class RecifradoService {

    private static final Logger log = LoggerFactory.getLogger(RecifradoService.class);
    static final int TAMANO_LOTE = 500;
    private static final String AUDITORIA = "POST /api/cientifico/ubicacion/recifrado";

    private final CifradorCoordenadas cifrador;
    private final ProveedorDeLlaves llaves;
    private final TransactionTemplate transacciones;
    private final AuditoriaAccesoUbicacion auditoria;
    private final Clock reloj;
    private final AtomicBoolean enCurso = new AtomicBoolean(false);

    private final EntityManager em;

    RecifradoService(EntityManager em, CifradorCoordenadas cifrador, ProveedorDeLlaves llaves, PlatformTransactionManager tm,
                     AuditoriaAccesoUbicacion auditoria, ObjectProvider<Clock> reloj) {
        this.em = em;
        this.cifrador = cifrador;
        this.llaves = llaves;
        this.transacciones = new TransactionTemplate(tm);
        this.auditoria = auditoria;
        this.reloj = reloj.getIfAvailable(Clock::systemUTC);
    }

    private record Lote(int leidos, int recifrados, int fallidos, byte[] ultimo) {
    }

    public RecifradoResponse recifrar(String usuario) {
        if (!enCurso.compareAndSet(false, true)) {
            throw new RecifradoEnCursoException();
        }
        try {
            String activa = llaves.versionActiva();
            int revisados = 0;
            int recifrados = 0;
            int fallidos = 0;
            byte[] ultimo = null;
            while (true) {
                final byte[] cursor = ultimo;
                Lote lote = transacciones.execute(estado -> procesarLote(activa, cursor));
                if (lote == null || lote.leidos() == 0) {
                    break;
                }
                revisados += lote.leidos();
                recifrados += lote.recifrados();
                fallidos += lote.fallidos();
                ultimo = lote.ultimo();
                if (lote.leidos() < TAMANO_LOTE) {
                    break;
                }
            }
            Map<String, Long> pendientes = transacciones.execute(estado -> pendientesPorVersion(activa));
            auditoria.registrar(usuario, AUDITORIA, true, "claveActiva=" + activa, recifrados, fallidos);
            return new RecifradoResponse(activa, revisados, recifrados, fallidos, pendientes);
        } finally {
            enCurso.set(false);
        }
    }

    @SuppressWarnings("unchecked")
    private Lote procesarLote(String activa, byte[] cursor) {
        String sql = "SELECT * FROM ubicacion_hallazgo WHERE coord_cifrada IS NOT NULL AND clave_version <> ?1"
                + (cursor != null ? " AND hallazgo_id > ?2" : "")
                + " ORDER BY hallazgo_id LIMIT " + TAMANO_LOTE + " FOR UPDATE SKIP LOCKED";
        Query q = em.createNativeQuery(sql, UbicacionHallazgo.class);
        q.setParameter(1, activa);
        if (cursor != null) {
            q.setParameter(2, cursor);
        }
        List<UbicacionHallazgo> filas = q.getResultList();
        int recifrados = 0;
        int fallidos = 0;
        Instant ahora = Instant.now(reloj);
        for (UbicacionHallazgo fila : filas) {
            CoordenadasCifradas actual = fila.coordenadasCifradas();
            try {
                fila.asignarCifrado(cifrador.recifrar(fila.getId(), actual), ahora);
                recifrados++;
            } catch (CoordenadasNoDescifrablesException e) {
                fallidos++;
                log.error("Re-cifrado omitido hallazgoId={} claveVersion={} motivo={}",
                        fila.getId(), actual.claveVersion(), e.motivo());
            }
        }
        em.flush();
        byte[] ultimo = filas.isEmpty() ? cursor : bytes(filas.get(filas.size() - 1).getId());
        return new Lote(filas.size(), recifrados, fallidos, ultimo);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> pendientesPorVersion(String activa) {
        Query q = em.createNativeQuery("SELECT clave_version, COUNT(*) FROM ubicacion_hallazgo"
                + " WHERE coord_cifrada IS NOT NULL AND clave_version <> ?1 GROUP BY clave_version ORDER BY clave_version");
        q.setParameter(1, activa);
        Map<String, Long> resultado = new LinkedHashMap<>();
        for (Object[] fila : (List<Object[]>) q.getResultList()) {
            resultado.put((String) fila[0], ((Number) fila[1]).longValue());
        }
        return resultado;
    }

    private static byte[] bytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }
}
