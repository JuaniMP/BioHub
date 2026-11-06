package co.edu.unbosque.biohub.gateway.service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.gateway.domain.ApiKeyRepository;

/**
 * Guarda el último uso de cada key cada 5 minutos. Escribir en cada petición gastaría una ida y
 * vuelta a Aiven y las pocas conexiones del plan gratuito solo para un dato informativo.
 */
@Component
public class RegistroUltimoUso {

    private static final Logger log = LoggerFactory.getLogger(RegistroUltimoUso.class);

    private final ApiKeyRepository repositorio;
    private final Map<UUID, Instant> pendientes = new ConcurrentHashMap<>();

    public RegistroUltimoUso(ApiKeyRepository repositorio) {
        this.repositorio = repositorio;
    }

    public void marcar(UUID keyId, Instant instante) {
        pendientes.put(keyId, instante);
    }

    @Scheduled(fixedDelayString = "${biohub.gateway.ultimo-uso.periodo:5m}")
    public void guardarPendientes() {
        for (Map.Entry<UUID, Instant> uso : pendientes.entrySet()) {
            try {
                repositorio.registrarUltimoUso(uso.getKey(), uso.getValue());
                pendientes.remove(uso.getKey(), uso.getValue());
            } catch (DataAccessException e) {
                log.warn("No se pudo guardar el último uso de las API Keys; se reintenta en el próximo ciclo");
                return;
            }
        }
    }
}
