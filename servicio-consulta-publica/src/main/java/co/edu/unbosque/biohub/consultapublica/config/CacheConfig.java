package co.edu.unbosque.biohub.consultapublica.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.CacheProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;

/**
 * Caché en Redis para las consultas públicas.
 * Valores en JSON (no serialización nativa de Java) y TTL de spring.cache.redis.time-to-live.
 * Cachear solo DTOs públicos: nunca datos sensibles (p. ej. ubicaciones exactas).
 */
@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig {

    @Bean
    RedisCacheConfiguration redisCacheConfiguration(CacheProperties props) {
        Duration ttl = props.getRedis().getTimeToLive();
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .serializeValuesWith(SerializationPair.fromSerializer(RedisSerializer.json()))
                .disableCachingNullValues()
                .prefixCacheNameWith("biohub:publica:");
        return ttl != null ? config.entryTtl(ttl) : config;
    }
}
