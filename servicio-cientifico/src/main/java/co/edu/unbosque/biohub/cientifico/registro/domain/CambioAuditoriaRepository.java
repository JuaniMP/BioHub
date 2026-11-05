package co.edu.unbosque.biohub.cientifico.registro.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Auditoría campo a campo (HU-05). Append-only: solo inserta y consulta. */
public interface CambioAuditoriaRepository extends JpaRepository<CambioAuditoria, Long> {

    List<CambioAuditoria> findByHallazgoIdOrderByCreadoEnDesc(UUID hallazgoId);
}
