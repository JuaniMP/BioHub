package co.edu.unbosque.biohub.gateway.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.gateway.domain.AlmacenCredenciales;
import co.edu.unbosque.biohub.gateway.domain.ApiKeyRepository;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorExterno;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorExternoRepository;

/** Alta de consumidores externos y configuración de su límite (HU-12, RNF11). */
@Service
public class ServicioConsumidores {

    private static final Logger auditoria = LoggerFactory.getLogger("biohub.auditoria.gateway");

    private final ConsumidorExternoRepository consumidores;
    private final ApiKeyRepository keys;
    private final AlmacenCredenciales credenciales;
    private final Clock reloj;

    public ServicioConsumidores(ConsumidorExternoRepository consumidores, ApiKeyRepository keys,
                                AlmacenCredenciales credenciales, Clock reloj) {
        this.consumidores = consumidores;
        this.keys = keys;
        this.credenciales = credenciales;
        this.reloj = reloj;
    }

    public ConsumidorExterno crear(String nombre, String contactoEmail, Integer limitePorMinuto, String actor) {
        String nombreLimpio = nombre.trim();
        if (consumidores.existsByNombre(nombreLimpio)) {
            throw new ConflictoException("Ya existe un consumidor con ese nombre.");
        }
        try {
            ConsumidorExterno creado = consumidores.save(new ConsumidorExterno(UUID.randomUUID(), nombreLimpio,
                    contactoEmail.trim(), limitePorMinuto, actor, ahora()));
            auditoria.info("consumidor_creado id={} actor={}", creado.getId(), actor);
            return creado;
        } catch (DataIntegrityViolationException e) {
            throw new ConflictoException("Ya existe un consumidor con ese nombre.");
        }
    }

    public Page<ConsumidorExterno> listar(int pagina, int tamano) {
        return consumidores.findAll(PageRequest.of(pagina, tamano, Sort.by("nombre").and(Sort.by("id"))));
    }

    public ConsumidorExterno obtener(UUID id) {
        return consumidores.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("El consumidor no existe."));
    }

    /** El límite viaja en la credencial cacheada: se invalidan sus keys para aplicarlo de inmediato. */
    public ConsumidorExterno cambiarLimite(UUID id, Integer limitePorMinuto, String actor) {
        ConsumidorExterno consumidor = obtener(id);
        consumidor.cambiarLimite(limitePorMinuto, ahora());
        ConsumidorExterno guardado = consumidores.save(consumidor);
        keys.findByConsumidorIdOrderByCreadaEnDesc(id).forEach(key -> credenciales.invalidar(key.getPrefijo()));
        auditoria.info("limite_cambiado consumidor={} limite={} actor={}", id, limitePorMinuto, actor);
        return guardado;
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
