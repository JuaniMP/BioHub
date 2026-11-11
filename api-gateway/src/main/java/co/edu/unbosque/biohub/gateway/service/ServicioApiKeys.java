package co.edu.unbosque.biohub.gateway.service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.gateway.domain.AlmacenCredenciales;
import co.edu.unbosque.biohub.gateway.domain.ApiKey;
import co.edu.unbosque.biohub.gateway.domain.ApiKeyRepository;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorAutenticado;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorExternoRepository;
import co.edu.unbosque.biohub.gateway.domain.CredencialApiKey;

/** Emite, valida y revoca API Keys (HU-12). */
@Service
public class ServicioApiKeys {

    /** Auditoría propia: nunca recibe la key, solo su prefijo. */
    private static final Logger auditoria = LoggerFactory.getLogger("biohub.auditoria.gateway");

    private final GeneradorApiKey generador;
    private final ApiKeyRepository keys;
    private final ConsumidorExternoRepository consumidores;
    private final AlmacenCredenciales credenciales;
    private final RegistroUltimoUso ultimoUso;
    private final Clock reloj;

    public ServicioApiKeys(GeneradorApiKey generador, ApiKeyRepository keys, ConsumidorExternoRepository consumidores,
                           AlmacenCredenciales credenciales, RegistroUltimoUso ultimoUso, Clock reloj) {
        this.generador = generador;
        this.keys = keys;
        this.consumidores = consumidores;
        this.credenciales = credenciales;
        this.ultimoUso = ultimoUso;
        this.reloj = reloj;
    }

    /** Key en claro + datos guardados. La key en claro solo existe en esta respuesta. */
    public record KeyEmitida(ApiKey registro, String key) {
        @Override
        public String toString() {
            return "KeyEmitida[id=" + registro.getId() + ", key=***]";
        }
    }

    /** Devuelve vacío ante una key ausente, mal formada, desconocida, revocada o vencida. */
    public Optional<ConsumidorAutenticado> validar(String keyPresentada) {
        Optional<String> prefijo = generador.prefijoDe(keyPresentada);
        if (prefijo.isEmpty()) {
            return Optional.empty();
        }
        Instant ahora = reloj.instant();
        Optional<CredencialApiKey> credencial = credenciales.porPrefijo(prefijo.get());
        if (credencial.isEmpty() || !generador.coincide(keyPresentada, credencial.get().hash())
                || !credencial.get().utilizableEn(ahora)) {
            return Optional.empty();
        }
        CredencialApiKey valida = credencial.get();
        ultimoUso.marcar(valida.keyId(), ahora);
        return Optional.of(new ConsumidorAutenticado(valida.consumidorId(), valida.keyId(), prefijo.get(),
                valida.limitePorMinuto()));
    }

    public KeyEmitida emitir(UUID consumidorId, Instant expiraEn, String actor) {
        if (!consumidores.existsById(consumidorId)) {
            throw new RecursoNoEncontradoException("El consumidor no existe.");
        }
        GeneradorApiKey.KeyGenerada generada = generador.generar();
        ApiKey registro = keys.save(new ApiKey(UUID.randomUUID(), consumidorId, generada.prefijo(), generada.hash(),
                expiraEn, actor, ahora()));
        auditoria.info("api_key_emitida prefijo={} consumidor={} actor={}", generada.prefijo(), consumidorId, actor);
        return new KeyEmitida(registro, generada.key());
    }

    public List<ApiKey> listar(UUID consumidorId) {
        if (!consumidores.existsById(consumidorId)) {
            throw new RecursoNoEncontradoException("El consumidor no existe.");
        }
        return keys.findByConsumidorIdOrderByCreadaEnDesc(consumidorId);
    }

    /**
     * Sin transacción externa a propósito: {@code save} confirma en la BD y recién entonces se
     * invalida la caché. Al revés, otra petición podría volver a cachear la key aún activa.
     */
    public void revocar(UUID keyId, String actor) {
        ApiKey key = keys.findById(keyId)
                .orElseThrow(() -> new RecursoNoEncontradoException("La API Key no existe."));
        key.revocar(actor, ahora());
        keys.save(key);
        credenciales.invalidar(key.getPrefijo());
        auditoria.info("api_key_revocada prefijo={} actor={}", key.getPrefijo(), actor);
    }

    private Instant ahora() {
        return reloj.instant().truncatedTo(ChronoUnit.MILLIS);
    }
}
