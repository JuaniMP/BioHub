package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.ProveedorDeLlaves;

/** Configuración propia del módulo (no se toca {@code cientifico.config}, zona compartida). */
@Configuration
@EnableConfigurationProperties(PropiedadesCifrado.class)
public class ConfiguracionUbicacion {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionUbicacion.class);

    @Bean
    ProveedorDeLlaves proveedorDeLlaves(PropiedadesCifrado propiedades, Environment entorno) {
        boolean sinClaves = propiedades.claves() == null || propiedades.claves().isBlank();
        if (sinClaves && propiedades.llaveEfimeraDev() && entorno.acceptsProfiles(Profiles.of("dev"))) {
            // SEC-J-06: la llave efímera solo es admisible contra una BD local; con una BD remota (p. ej. Aiven)
            // las coordenadas quedarían cifradas con una llave que desaparece al reiniciar: pérdida irreversible
            String url = entorno.getProperty("spring.datasource.url", "");
            if (!esBaseLocal(url)) {
                throw new IllegalStateException("UBICACION_LLAVE_EFIMERA_DEV solo se permite con una BD local "
                        + "(spring.datasource.url en localhost o 127.0.0.1); configura UBICACION_CLAVES y UBICACION_CLAVE_ACTIVA");
            }
            log.error("Perfil dev: se usa una llave de cifrado EFÍMERA en memoria ({}); "
                    + "las coordenadas guardadas serán ilegibles tras reiniciar. NUNCA usar con datos reales",
                    ProveedorDeLlavesEntorno.ID_EFIMERA);
            return ProveedorDeLlavesEntorno.efimera();
        }
        return new ProveedorDeLlavesEntorno(propiedades.claves(), propiedades.claveActiva());
    }

    private static final java.util.regex.Pattern BD_LOCAL = java.util.regex.Pattern.compile(
            "^jdbc:[a-z0-9:]+://(?:[^/@?]*@)?(?:localhost|127\\.0\\.0\\.1|\\[::1\\])(?::\\d{1,5})?(?:[/?;].*)?$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /** {@code true} solo si el único host de la URL JDBC es esta misma máquina (no basta que la cadena contenga "localhost"). */
    static boolean esBaseLocal(String url) {
        return url != null && BD_LOCAL.matcher(url.trim()).matches();
    }

    @Bean
    CifradorCoordenadas cifradorCoordenadas(ProveedorDeLlaves proveedor) {
        return new CifradorAesGcm(proveedor);
    }
}
