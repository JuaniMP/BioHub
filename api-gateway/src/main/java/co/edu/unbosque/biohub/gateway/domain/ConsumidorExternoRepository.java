package co.edu.unbosque.biohub.gateway.domain;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumidorExternoRepository extends JpaRepository<ConsumidorExterno, UUID> {

    boolean existsByNombre(String nombre);
}
