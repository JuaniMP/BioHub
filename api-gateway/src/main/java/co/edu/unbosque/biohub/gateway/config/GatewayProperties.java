package co.edu.unbosque.biohub.gateway.config;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.util.unit.DataSize;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del gateway (prefijo biohub.gateway en application.yml).
 *
 * @param rutas                prefijo de ruta (/api/&lt;prefijo&gt;/**) → URL base del servicio destino
 * @param rutasPublicas        prefijos que NO exigen JWT (se protegerán con API Key + rate limiting)
 * @param origenesPermitidos   orígenes CORS (las dos webs)
 * @param timeoutConexion      tiempo máximo para conectar con el servicio destino
 * @param timeoutLectura       tiempo máximo de espera de la respuesta del servicio destino
 * @param tamanoMaximoCuerpo   tamaño máximo del cuerpo que el gateway acepta reenviar (413 si se supera)
 */
@ConfigurationProperties(prefix = "biohub.gateway")
public record GatewayProperties(
        Map<String, String> rutas,
        List<String> rutasPublicas,
        List<String> origenesPermitidos,
        Duration timeoutConexion,
        Duration timeoutLectura,
        DataSize tamanoMaximoCuerpo) {

    public GatewayProperties {
        rutas = rutas == null ? Map.of() : Map.copyOf(rutas);
        rutasPublicas = rutasPublicas == null ? List.of() : List.copyOf(rutasPublicas);
        origenesPermitidos = origenesPermitidos == null ? List.of() : List.copyOf(origenesPermitidos);
        timeoutConexion = timeoutConexion == null ? Duration.ofSeconds(2) : timeoutConexion;
        timeoutLectura = timeoutLectura == null ? Duration.ofSeconds(30) : timeoutLectura;
        tamanoMaximoCuerpo = tamanoMaximoCuerpo == null ? DataSize.ofMegabytes(10) : tamanoMaximoCuerpo;
    }
}
