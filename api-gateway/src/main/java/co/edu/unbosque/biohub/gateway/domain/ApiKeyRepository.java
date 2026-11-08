package co.edu.unbosque.biohub.gateway.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, UUID> {

    List<ApiKey> findByConsumidorIdOrderByCreadaEnDesc(UUID consumidorId);

    @Query("""
            select new co.edu.unbosque.biohub.gateway.domain.CredencialApiKey(
                k.id, c.id, k.hashSha256, k.estado, k.expiraEn, c.limitePorMinuto)
            from ApiKey k, ConsumidorExterno c
            where c.id = k.consumidorId and k.prefijo = :prefijo
            """)
    Optional<CredencialApiKey> buscarCredencial(@Param("prefijo") String prefijo);

    @Transactional
    @Modifying
    @Query("update ApiKey k set k.ultimoUsoEn = :instante where k.id = :id")
    int registrarUltimoUso(@Param("id") UUID id, @Param("instante") Instant instante);
}
