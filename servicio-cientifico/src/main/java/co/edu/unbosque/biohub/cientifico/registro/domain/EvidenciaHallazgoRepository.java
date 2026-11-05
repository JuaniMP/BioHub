package co.edu.unbosque.biohub.cientifico.registro.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Evidencias (fotografías y audios) de un hallazgo (HU-01). */
public interface EvidenciaHallazgoRepository extends JpaRepository<EvidenciaHallazgo, UUID> {

    List<EvidenciaHallazgo> findByHallazgoIdOrderByCreadoEnAsc(UUID hallazgoId);

    boolean existsByHallazgoIdAndSha256(UUID hallazgoId, String sha256);
}
