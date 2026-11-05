package co.edu.unbosque.biohub.cientifico.registro.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Acceso a los hallazgos. La búsqueda interna con filtros combinables es de ubicación (HU-13). */
public interface HallazgoRepository extends JpaRepository<Hallazgo, UUID> {

    /** Hallazgos de un investigador, del más reciente al más antiguo. */
    Page<Hallazgo> findByInvestigadorOrderByFechaRecoleccionDesc(String investigador, Pageable pagina);

    /** Hallazgos vigentes de una especie (los anulados no cuentan). */
    @Query("select h from Hallazgo h where h.nombreCientifico = :especie and h.anuladoEn is null")
    Page<Hallazgo> buscarVigentesPorEspecie(@Param("especie") String especie, Pageable pagina);

    /** Un hallazgo que no esté anulado; para operaciones que no deben tocar hallazgos retirados. */
    @Query("select h from Hallazgo h where h.id = :id and h.anuladoEn is null")
    Optional<Hallazgo> buscarVigente(@Param("id") UUID id);
}
