package co.edu.unbosque.biohub.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

/**
 * Conexión del gateway con la API de administración de Keycloak (prefijo biohub.gateway.identidad).
 *
 * @param keycloakUrl  URL base de Keycloak
 * @param realm        realm de BioHub
 * @param clientId     cliente confidencial del gateway (cuenta de servicio)
 * @param clientSecret secreto de ese cliente; si está vacío, la administración de usuarios responde 503
 * @param vidaToken    duración de un access token (accessTokenLifespan del realm)
 */
@ConfigurationProperties(prefix = "biohub.gateway.identidad")
public record PropiedadesIdentidad(String keycloakUrl, String realm, String clientId, String clientSecret,
                                   Duration vidaToken) {

    public PropiedadesIdentidad {
        vidaToken = vidaToken == null ? Duration.ofMinutes(5) : vidaToken;
    }

    public boolean configurada() {
        return StringUtils.hasText(keycloakUrl) && StringUtils.hasText(clientSecret);
    }

    /** Nunca imprime el secreto. */
    @Override
    public String toString() {
        return "PropiedadesIdentidad[keycloakUrl=" + keycloakUrl + ", realm=" + realm + ", clientId=" + clientId + "]";
    }
}
