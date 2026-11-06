package co.edu.unbosque.biohub.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Límites de peticiones (prefijo biohub.gateway.limites en application.yml).
 *
 * @param porConsumidor  peticiones por ventana de un consumidor externo sin límite propio (RNF11: 60)
 * @param porIpPortal    peticiones por ventana de una IP en el portal público (D16)
 * @param fallosKeyPorIp intentos con API Key inválida por ventana y por IP
 * @param ventana        duración de la ventana
 */
@ConfigurationProperties(prefix = "biohub.gateway.limites")
public record PropiedadesLimites(int porConsumidor, int porIpPortal, int fallosKeyPorIp, Duration ventana) {

    public PropiedadesLimites {
        porConsumidor = porConsumidor > 0 ? porConsumidor : 60;
        porIpPortal = porIpPortal > 0 ? porIpPortal : 120;
        fallosKeyPorIp = fallosKeyPorIp > 0 ? fallosKeyPorIp : 20;
        ventana = ventana == null ? Duration.ofSeconds(60) : ventana;
    }
}
