package co.edu.unbosque.biohub.cientifico.registro.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Historial inmutable (HU-05). Solo inserta y consulta: no declara ninguna operación de modificación
 * ni de borrado, y la base de datos tampoco las permite.
 */
public interface VersionHallazgoRepository extends JpaRepository<VersionHallazgo, UUID> {

    /** Todas las versiones de un hallazgo, de la más reciente a la más antigua. */
    List<VersionHallazgo> findByHallazgoIdOrderByVersionDesc(UUID hallazgoId);

    /** Una versión puntual, para comparar dos momentos del hallazgo. */
    Optional<VersionHallazgo> findByHallazgoIdAndVersion(UUID hallazgoId, int version);
}
