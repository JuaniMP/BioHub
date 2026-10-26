package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UbicacionHallazgoRepository extends JpaRepository<UbicacionHallazgo, UUID> {
}
