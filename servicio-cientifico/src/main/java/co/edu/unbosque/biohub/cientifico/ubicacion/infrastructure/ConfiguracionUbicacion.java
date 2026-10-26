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
            log.warn("Perfil dev: se usa una llave de cifrado EFÍMERA en memoria; "
                    + "las coordenadas guardadas serán ilegibles tras reiniciar");
            return ProveedorDeLlavesEntorno.efimera();
        }
        return new ProveedorDeLlavesEntorno(propiedades.claves(), propiedades.claveActiva());
    }

    @Bean
    CifradorCoordenadas cifradorCoordenadas(ProveedorDeLlaves proveedor) {
        return new CifradorAesGcm(proveedor);
    }
}
