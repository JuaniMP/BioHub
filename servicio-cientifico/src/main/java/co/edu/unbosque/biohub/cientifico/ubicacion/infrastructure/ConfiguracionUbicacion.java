package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.ProveedorDeLlaves;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgoRepository;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.AuditoriaAccesoUbicacion;
import jakarta.persistence.EntityManager;

/** Configuración propia del módulo (no se toca {@code cientifico.config}, zona compartida). */
@Configuration
@EnableConfigurationProperties(PropiedadesCifrado.class)
public class ConfiguracionUbicacion {

    private static final Logger log = LoggerFactory.getLogger(ConfiguracionUbicacion.class);

    /**
     * Elige el adaptador del puerto {@link ProveedorDeLlaves} según {@code biohub.ubicacion.cifrado.proveedor}
     * ({@code entorno} por defecto, o {@code keystore}); el resto del módulo no se entera (ARQ-11, SAD §6.3.1).
     */
    @Bean
    ProveedorDeLlaves proveedorDeLlaves(PropiedadesCifrado propiedades, Environment entorno) {
        if (PropiedadesCifrado.PROVEEDOR_KEYSTORE.equals(propiedades.proveedor())) {
            String ruta = propiedades.keystoreRuta();
            String clave = propiedades.keystorePassword();
            if (ruta == null || ruta.isBlank()) {
                throw new IllegalStateException("UBICACION_KEYSTORE_RUTA es obligatoria con el proveedor keystore");
            }
            // SEC-J-12: la copia en char[] no debe sobrevivir al arranque; el adaptador solo la usa al abrir el .p12
            char[] contrasena = clave == null ? null : clave.toCharArray();
            try {
                return new ProveedorDeLlavesKeyStore(java.nio.file.Path.of(ruta.trim()), contrasena,
                        propiedades.claveActiva());
            } finally {
                if (contrasena != null) {
                    java.util.Arrays.fill(contrasena, '\0');
                }
            }
        }
        if (!PropiedadesCifrado.PROVEEDOR_ENTORNO.equals(propiedades.proveedor())) {
            throw new IllegalStateException("biohub.ubicacion.cifrado.proveedor debe ser 'entorno' o 'keystore'");
        }
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

    /**
     * Arma la cadena del Decorator (SAD §6.3.2, ARQ-10): auditoría -> cifrado -> JPA. Es el único
     * {@link RepositorioUbicaciones} del contexto; los servicios inyectan la interfaz y no pueden saltarse
     * el cifrado ni la auditoría.
     */
    @Bean
    RepositorioUbicaciones repositorioUbicaciones(UbicacionHallazgoRepository jpa, EntityManager em,
                                                  CifradorCoordenadas cifrador, AuditoriaAccesoUbicacion auditoria,
                                                  ObjectProvider<Clock> reloj) {
        RepositorioUbicaciones base = new RepositorioUbicacionesJpa(jpa, em);
        RepositorioUbicaciones cifrado = new DecoradorCifradoCoordenadas(base, cifrador,
                reloj.getIfAvailable(Clock::systemUTC));
        return new DecoradorAuditoriaAcceso(cifrado, auditoria);
    }
}
