package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.KeyStore;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.ProveedorDeLlaves;

/**
 * Adapter del almacén de llaves del SAD (§6.3.1, §4.7, ASR6): lee las llaves AES-256 de un keystore PKCS12
 * ({@code .p12}) protegido con contraseña. Cada alias es el id de una versión ({@code v1}, {@code v2}...) y la
 * versión activa viene de {@code UBICACION_CLAVE_ACTIVA}, igual que con {@link ProveedorDeLlavesEntorno}: es la
 * segunda implementación del puerto {@link ProveedorDeLlaves}, y el resto del módulo no se entera.
 *
 * <p>Valida al construirse y falla con {@link IllegalStateException} (el servicio no arranca) sin mostrar nunca
 * la contraseña ni el material de las llaves. La contraseña llega por variable de entorno; el {@code .p12} no se
 * versiona (ver servicio-cientifico/README.md para crearlo con {@code keytool}).
 */
public final class ProveedorDeLlavesKeyStore implements ProveedorDeLlaves {

    private static final Pattern ID_VALIDO = Pattern.compile("^v[1-9][0-9]{0,3}$");
    private static final int BYTES_LLAVE = 32;

    private final Map<String, SecretKey> llaves;
    private final String activa;

    public ProveedorDeLlavesKeyStore(Path ruta, char[] contrasena, String claveActiva) {
        if (ruta == null || !Files.isRegularFile(ruta)) {
            throw new IllegalStateException("UBICACION_KEYSTORE_RUTA: el archivo del keystore no existe");
        }
        if (contrasena == null || contrasena.length == 0) {
            throw new IllegalStateException("UBICACION_KEYSTORE_PASSWORD no está configurada");
        }
        this.llaves = Collections.unmodifiableMap(cargar(ruta, contrasena));
        if (llaves.isEmpty()) {
            throw new IllegalStateException("El keystore no contiene llaves AES con alias vN (v1, v2...)");
        }
        if (claveActiva == null || claveActiva.isBlank() || !llaves.containsKey(claveActiva.trim())) {
            throw new IllegalStateException(
                    "UBICACION_CLAVE_ACTIVA no está configurada o no existe como alias en el keystore");
        }
        this.activa = claveActiva.trim();
    }

    private static Map<String, SecretKey> cargar(Path ruta, char[] contrasena) {
        Map<String, SecretKey> resultado = new LinkedHashMap<>();
        try (InputStream in = Files.newInputStream(ruta)) {
            KeyStore almacen = KeyStore.getInstance("PKCS12");
            almacen.load(in, contrasena);
            for (String alias : Collections.list(almacen.aliases())) {
                if (!almacen.isKeyEntry(alias)) {
                    continue; // certificados de confianza u otros: no son llaves de cifrado
                }
                if (!ID_VALIDO.matcher(alias).matches()) {
                    throw llaveInvalida("?");
                }
                Key llave = almacen.getKey(alias, contrasena);
                if (!(llave instanceof SecretKey secreta) || !"AES".equalsIgnoreCase(llave.getAlgorithm())
                        || secreta.getEncoded() == null || secreta.getEncoded().length != BYTES_LLAVE) {
                    throw llaveInvalida(alias);
                }
                // Copia a SecretKeySpec para no depender del proveedor del keystore al cifrar
                resultado.put(alias, new SecretKeySpec(secreta.getEncoded(), "AES"));
            }
        } catch (IOException | GeneralSecurityException e) {
            // Sin la causa: podría arrastrar rutas o detalles del contenido; basta con saber que no se pudo abrir
            throw new IllegalStateException("No se pudo abrir el keystore PKCS12: ruta, contraseña o formato incorrectos");
        }
        return resultado;
    }

    private static IllegalStateException llaveInvalida(String alias) {
        return new IllegalStateException("Keystore: la entrada '" + alias
                + "' no es una llave AES de 32 bytes con alias vN");
    }

    @Override
    public String versionActiva() {
        return activa;
    }

    @Override
    public Optional<SecretKey> llave(String version) {
        return Optional.ofNullable(version == null ? null : llaves.get(version));
    }

    @Override
    public Set<String> versiones() {
        return llaves.keySet();
    }

    @Override
    public String toString() {
        return "ProveedorDeLlavesKeyStore[activa=" + activa + ", versiones=" + llaves.keySet() + "]";
    }
}
