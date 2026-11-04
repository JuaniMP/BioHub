package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * Acceso Spring Data a {@code ubicacion_hallazgo}. A propósito extiende {@link Repository} y no JpaRepository
 * (ARQ-10): no expone {@code delete*}, {@code deleteAll}, {@code saveAll} ni {@code findAll}. Solo lo usa
 * {@code RepositorioUbicacionesJpa}; el resto del módulo trabaja con {@link RepositorioUbicaciones}.
 */
public interface UbicacionHallazgoRepository extends Repository<UbicacionHallazgo, UUID> {

    boolean existsById(UUID id);

    Optional<UbicacionHallazgo> findById(UUID id);

    <S extends UbicacionHallazgo> S saveAndFlush(S entidad);
}
