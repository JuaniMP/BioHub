package co.edu.unbosque.biohub.consultapublica.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.data.redis.LettuceClientConfigurationBuilderCustomizer;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.BatchStrategies;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import co.edu.unbosque.biohub.consultapublica.controller.FichaPublicaResponse;
import co.edu.unbosque.biohub.consultapublica.controller.PaginaFichasResponse;
import co.edu.unbosque.biohub.consultapublica.infrastructure.CacheErrorHandlerTolerante;
import co.edu.unbosque.biohub.consultapublica.infrastructure.CacheManagerTolerante;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.TimeoutOptions;

/**
 * Caché pública en Redis (D25). Dos cachés con TTL propio y valores JSON tipados (sin tipado polimórfico:
 * solo se deserializan los DTOs públicos esperados):
 * <ul>
 *   <li>{@code fichas}: 5 min, invalidación por id al aplicar un evento.</li>
 *   <li>{@code busquedas}: 60 s, se vacía con SCAN (lotes de 1000) tras cada evento aplicado.</li>
 * </ul>
 * Cachear solo DTOs públicos: nunca coordenadas ni datos del investigador. Redis caído no tumba el servicio:
 * los errores se tratan como miss ({@link CacheErrorHandlerTolerante}) y Lettuce rechaza los comandos de inmediato
 * mientras está desconectado, así que no se espera el timeout en cada petición.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String FICHAS = "fichas";
    public static final String BUSQUEDAS = "busquedas";

    private final CacheErrorHandler errores = new CacheErrorHandlerTolerante();

    @Bean
    CacheManager cacheManager(RedisConnectionFactory conexion,
                              @Value("${biohub.cache.ttl-fichas:5m}") Duration ttlFichas,
                              @Value("${biohub.cache.ttl-busquedas:60s}") Duration ttlBusquedas) {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .prefixCacheNameWith("biohub:publica:");

        RedisCacheWriter escritor = RedisCacheWriter.nonLockingRedisCacheWriter(conexion, BatchStrategies.scan(1000));
        RedisCacheManager redis = RedisCacheManager.builder(escritor)
                .cacheDefaults(base.entryTtl(ttlBusquedas))
                .withCacheConfiguration(FICHAS, base.entryTtl(ttlFichas).serializeValuesWith(
                        SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, FichaPublicaResponse.class))))
                .withCacheConfiguration(BUSQUEDAS, base.entryTtl(ttlBusquedas).serializeValuesWith(
                        SerializationPair.fromSerializer(new Jackson2JsonRedisSerializer<>(mapper, PaginaFichasResponse.class))))
                .build();
        redis.afterPropertiesSet(); // al envolverlo, Spring ya no lo inicializa: sin esto se perdería la configuración por caché
        return new CacheManagerTolerante(redis, errores);
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return errores;
    }

    /** Falla rápido si Redis está desconectado (reconecta solo) en vez de esperar el timeout en cada comando. */
    @Bean
    LettuceClientConfigurationBuilderCustomizer lettuceFallaRapido(
            @Value("${spring.data.redis.connect-timeout:300ms}") Duration timeoutConexion) {
        return builder -> builder.clientOptions(ClientOptions.builder()
                .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                .socketOptions(SocketOptions.builder().connectTimeout(timeoutConexion).build())
                .timeoutOptions(TimeoutOptions.enabled())
                .build());
    }
}
