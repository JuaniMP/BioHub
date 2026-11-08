package co.edu.unbosque.biohub.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Circuit breaker hacia los servicios internos (prefijo biohub.gateway.circuito, SAD §5.1, ARQ-20).
 *
 * @param fallosParaAbrir fallos de conexión seguidos que abren el circuito de un servicio
 * @param tiempoAbierto   cuánto responde 503 de inmediato antes de volver a probar
 */
@ConfigurationProperties(prefix = "biohub.gateway.circuito")
public record PropiedadesCircuito(int fallosParaAbrir, Duration tiempoAbierto) {

    public PropiedadesCircuito {
        fallosParaAbrir = fallosParaAbrir > 0 ? fallosParaAbrir : 5;
        tiempoAbierto = tiempoAbierto == null ? Duration.ofSeconds(10) : tiempoAbierto;
    }
}
