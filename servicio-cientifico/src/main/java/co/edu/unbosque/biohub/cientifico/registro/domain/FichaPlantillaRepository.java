package co.edu.unbosque.biohub.cientifico.registro.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/** Plantillas de ficha taxonómica (HU-02). Cada cambio agrega una versión; ninguna se modifica. */
public interface FichaPlantillaRepository extends JpaRepository<FichaPlantilla, UUID> {

    /** Versión vigente de la plantilla de una especie (la de número más alto). */
    Optional<FichaPlantilla> findFirstByNombreCientificoOrderByVersionDesc(String nombreCientifico);

    Optional<FichaPlantilla> findByNombreCientificoAndVersion(String nombreCientifico, int version);
}
