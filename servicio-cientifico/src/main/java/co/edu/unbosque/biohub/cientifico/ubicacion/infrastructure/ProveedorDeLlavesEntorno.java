package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import java.util.Base64;
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
 * Llaves AES-256 leídas del entorno ({@code UBICACION_CLAVES} / {@code UBICACION_CLAVE_ACTIVA}).
 * Valida al construirse: si algo falla lanza {@link IllegalStateException} (el servicio no arranca)
 * con un mensaje que nombra la variable y la regla, nunca el valor.
 */
public final class ProveedorDeLlavesEntorno implements ProveedorDeLlaves {

    private static final Pattern ID_VALIDO = Pattern.compile("^v[1-9][0-9]{0,3}$");
    private static final int BYTES_LLAVE = 32;
    /** Id de la llave efímera de desarrollo: no puede confundirse con una llave real (v1, v2...) al diagnosticar (SEC-J-06). */
    public static final String ID_EFIMERA = "v9999";

    private final Map<String, SecretKey> llaves;
    private final String activa;

    public ProveedorDeLlavesEntorno(String claves, String claveActiva) {
        this.llaves = Collections.unmodifiableMap(parsear(claves));
        if (claveActiva == null || claveActiva.isBlank() || !llaves.containsKey(claveActiva.trim())) {
            throw new IllegalStateException(
                    "UBICACION_CLAVE_ACTIVA no está configurada o no existe en UBICACION_CLAVES");
        }
        this.activa = claveActiva.trim();
    }

    /** Llave efímera en memoria para desarrollo local (perfil dev explícito). */
    public static ProveedorDeLlavesEntorno efimera() {
        byte[] bytes = new byte[BYTES_LLAVE];
        new java.security.SecureRandom().nextBytes(bytes);
        return new ProveedorDeLlavesEntorno(ID_EFIMERA + ":" + Base64.getEncoder().encodeToString(bytes), ID_EFIMERA);
    }

    private static Map<String, SecretKey> parsear(String claves) {
        Map<String, SecretKey> resultado = new LinkedHashMap<>();
        if (claves == null || claves.isBlank()) {
            return resultado;
        }
        for (String par : claves.split(",")) {
            String texto = par.trim();
            int separador = texto.indexOf(':');
            String id = separador < 0 ? texto : texto.substring(0, separador).trim();
            String base64 = separador < 0 ? "" : texto.substring(separador + 1).trim();
            if (!ID_VALIDO.matcher(id).matches() || resultado.containsKey(id)) {
                throw llaveInvalida(id);
            }
            byte[] bytes;
            try {
                bytes = Base64.getDecoder().decode(base64);
            } catch (IllegalArgumentException e) {
                throw llaveInvalida(id);
            }
            if (bytes.length != BYTES_LLAVE) {
                throw llaveInvalida(id);
            }
            resultado.put(id, new SecretKeySpec(bytes, "AES"));
        }
        return resultado;
    }

    private static IllegalStateException llaveInvalida(String id) {
        String idSeguro = ID_VALIDO.matcher(id).matches() ? id : "?";
        return new IllegalStateException("UBICACION_CLAVES: la llave '" + idSeguro
                + "' no es válida (se esperan 32 bytes en base64, id con formato vN y sin repetir)");
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
        return "ProveedorDeLlavesEntorno[activa=" + activa + ", versiones=" + llaves.keySet() + "]";
    }
}
