package co.edu.unbosque.biohub.cientifico.registro.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.cientifico.registro.domain.CambioAuditoria;
import co.edu.unbosque.biohub.cientifico.registro.domain.CambioAuditoriaRepository;
import co.edu.unbosque.biohub.cientifico.registro.domain.VersionHallazgo;
import co.edu.unbosque.biohub.cientifico.registro.domain.VersionHallazgoRepository;

/**
 * Consulta del historial inmutable de un hallazgo y comparación entre versiones (HU-05).
 *
 * <p>Solo lee. Las versiones y la auditoría se escriben una sola vez, al registrar o reclasificar, y
 * la base de datos impide modificarlas o borrarlas.
 */
@Service
public class HistorialService {

    private final VersionHallazgoRepository versiones;
    private final CambioAuditoriaRepository auditoria;

    public HistorialService(VersionHallazgoRepository versiones, CambioAuditoriaRepository auditoria) {
        this.versiones = versiones;
        this.auditoria = auditoria;
    }

    /** Todas las versiones de un hallazgo, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public List<VersionHallazgo> versionesDe(UUID hallazgoId) {
        List<VersionHallazgo> historial = versiones.findByHallazgoIdOrderByVersionDesc(hallazgoId);
        if (historial.isEmpty()) {
            throw new HallazgoNoEncontradoException(hallazgoId);
        }
        return historial;
    }

    /** Cambios campo a campo registrados para un hallazgo, del más reciente al más antiguo. */
    @Transactional(readOnly = true)
    public List<CambioAuditoria> cambiosDe(UUID hallazgoId) {
        return auditoria.findByHallazgoIdOrderByCreadoEnDesc(hallazgoId);
    }

    /**
     * Compara dos versiones y devuelve solo lo que cambió, que es lo que el auditor necesita ver
     * resaltado (HU-05, criterio 3).
     *
     * @throws HallazgoNoEncontradoException si alguna de las dos versiones no existe
     * @throws DatosHallazgoInvalidosException si ambas versiones son la misma
     */
    @Transactional(readOnly = true)
    public ComparacionVersiones comparar(UUID hallazgoId, int versionA, int versionB) {
        if (versionA == versionB) {
            throw new DatosHallazgoInvalidosException("version", "las dos versiones deben ser distintas");
        }
        int anterior = Math.min(versionA, versionB);
        int nueva = Math.max(versionA, versionB);

        VersionHallazgo desde = buscar(hallazgoId, anterior);
        VersionHallazgo hasta = buscar(hallazgoId, nueva);

        return new ComparacionVersiones(hallazgoId, anterior, nueva, hasta.getJustificacion(), hasta.getAutor(),
                diferencias(desde.getSnapshot(), hasta.getSnapshot()));
    }

    private VersionHallazgo buscar(UUID hallazgoId, int version) {
        return versiones.findByHallazgoIdAndVersion(hallazgoId, version)
                .orElseThrow(() -> new HallazgoNoEncontradoException(hallazgoId));
    }

    /** Compara dos snapshots por clave, incluyendo las que aparecen o desaparecen entre versiones. */
    private static List<DiferenciaCampo> diferencias(Map<String, Object> desde, Map<String, Object> hasta) {
        Set<String> claves = new LinkedHashSet<>(desde.keySet());
        claves.addAll(hasta.keySet());
        // La versión viaja en el snapshot y siempre cambia: no aporta nada como diferencia.
        claves.remove("version");

        List<DiferenciaCampo> cambios = new ArrayList<>();
        for (String clave : claves) {
            Object anterior = desde.get(clave);
            Object actual = hasta.get(clave);
            if (!Objects.equals(anterior, actual)) {
                cambios.add(new DiferenciaCampo(clave, anterior, actual));
            }
        }
        return cambios;
    }
}
