package co.edu.unbosque.biohub.gateway.infrastructure.credenciales;

import java.time.Duration;
import java.util.Optional;

import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.gateway.domain.AlmacenCredenciales;
import co.edu.unbosque.biohub.gateway.domain.CredencialApiKey;
import co.edu.unbosque.biohub.gateway.infrastructure.InterruptorRedis;

/**
 * Decorator: añade una caché en Redis delante de la BD, porque cada consulta a Aiven cruza internet.
 *
 * <p>Revocar no borra la entrada: deja una marca que dura lo mismo que la caché. Si solo se borrara,
 * una petición que ya leyó la key activa de la BD podría volver a guardarla justo después. Con la
 * marca y {@code setIfAbsent}, esa escritura tardía se descarta y la key revocada no reaparece.
 */
@Component
@Primary
public class AlmacenCredencialesEnCache implements AlmacenCredenciales {

    private static final String PREFIJO = "biohub:gateway:key:";
    private static final String INVALIDADA = "invalidada";
    private static final Duration VIDA = Duration.ofSeconds(60);

    private final AlmacenCredencialesJpa origen;
    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final InterruptorRedis interruptor;

    public AlmacenCredencialesEnCache(AlmacenCredencialesJpa origen, StringRedisTemplate redis, ObjectMapper json,
                                      InterruptorRedis interruptor) {
        this.origen = origen;
        this.redis = redis;
        this.json = json;
        this.interruptor = interruptor;
    }

    @Override
    public Optional<CredencialApiKey> porPrefijo(String prefijo) {
        String guardado = leer(prefijo);
        if (guardado != null && !INVALIDADA.equals(guardado)) {
            Optional<CredencialApiKey> credencial = interpretar(guardado);
            if (credencial.isPresent()) {
                return credencial;
            }
        }
        Optional<CredencialApiKey> credencial = origen.porPrefijo(prefijo);
        if (guardado == null) {
            credencial.ifPresent(valor -> guardar(prefijo, valor));
        }
        return credencial;
    }

    @Override
    public void invalidar(String prefijo) {
        origen.invalidar(prefijo);
        try {
            redis.opsForValue().set(PREFIJO + prefijo, INVALIDADA, VIDA);
        } catch (DataAccessException e) {
            // Mientras Redis esté caído se lee de la BD. Si vuelve antes de 60 s, la entrada vieja dura
            // como mucho lo que le quede de vida.
            interruptor.registrarFallo(e);
        }
    }

    private String leer(String prefijo) {
        if (!interruptor.disponible()) {
            return null;
        }
        try {
            return redis.opsForValue().get(PREFIJO + prefijo);
        } catch (DataAccessException e) {
            interruptor.registrarFallo(e);
            return null;
        }
    }

    private void guardar(String prefijo, CredencialApiKey credencial) {
        if (!interruptor.disponible()) {
            return;
        }
        try {
            redis.opsForValue().setIfAbsent(PREFIJO + prefijo, json.writeValueAsString(credencial), VIDA);
        } catch (DataAccessException e) {
            interruptor.registrarFallo(e);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo serializar la credencial", e);
        }
    }

    private Optional<CredencialApiKey> interpretar(String guardado) {
        try {
            return Optional.of(json.readValue(guardado, CredencialApiKey.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }
}
