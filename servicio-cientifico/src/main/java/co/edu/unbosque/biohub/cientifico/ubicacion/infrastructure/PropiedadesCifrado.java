package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuración del cifrado ({@code biohub.ubicacion.cifrado.*}). Su {@code toString()} no expone las llaves
 * ni la contraseña del keystore.
 *
 * @param claves           lista "id:base64" separada por comas (UBICACION_CLAVES); solo proveedor "entorno"
 * @param claveActiva      id de la llave con que se cifra (UBICACION_CLAVE_ACTIVA); en "keystore" es un alias
 * @param llaveEfimeraDev  solo con perfil "dev" y proveedor "entorno": genera una llave efímera si no hay claves
 * @param proveedor        "entorno" (por defecto) o "keystore" (ARQ-11: PKCS12 con contraseña)
 * @param keystoreRuta     ruta del .p12 (UBICACION_KEYSTORE_RUTA); solo proveedor "keystore"
 * @param keystorePassword contraseña del .p12 (UBICACION_KEYSTORE_PASSWORD, solo por entorno)
 */
@ConfigurationProperties("biohub.ubicacion.cifrado")
public record PropiedadesCifrado(String claves, String claveActiva, boolean llaveEfimeraDev, String proveedor,
                                 String keystoreRuta, String keystorePassword) {

    public static final String PROVEEDOR_ENTORNO = "entorno";
    public static final String PROVEEDOR_KEYSTORE = "keystore";

    public PropiedadesCifrado {
        proveedor = proveedor == null || proveedor.isBlank() ? PROVEEDOR_ENTORNO : proveedor.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public String toString() {
        return "PropiedadesCifrado[proveedor=" + proveedor + ", claves=***, claveActiva=" + claveActiva
                + ", keystorePassword=***]";
    }
}
