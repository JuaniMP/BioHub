package co.edu.unbosque.biohub.gateway.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Crea y reconoce API Keys con la forma {@code bh_pub_<8 caracteres>_<43 caracteres>}.
 * El secreto son 32 bytes aleatorios: por eso basta SHA-256, sin sal ni un hash lento. Nadie puede
 * adivinar 256 bits al azar, a diferencia de una contraseña elegida por una persona.
 */
@Component
public class GeneradorApiKey {

    private static final String INICIO = "bh_pub_";
    private static final int LARGO_IDENTIFICADOR = 8;
    private static final int BYTES_SECRETO = 32;
    private static final String ALFABETO = "abcdefghijkmnpqrstuvwxyz23456789";
    private static final Pattern FORMATO =
            Pattern.compile("^bh_pub_[a-z0-9]{" + LARGO_IDENTIFICADOR + "}_[A-Za-z0-9_-]{43}$");

    private final SecureRandom aleatorio = new SecureRandom();

    /** La key completa solo existe en este objeto: se entrega una vez y no se guarda. */
    public record KeyGenerada(String prefijo, String key, byte[] hash) {
        @Override
        public String toString() {
            return "KeyGenerada[prefijo=" + prefijo + ", key=***]";
        }
    }

    public KeyGenerada generar() {
        StringBuilder prefijo = new StringBuilder(INICIO);
        for (int i = 0; i < LARGO_IDENTIFICADOR; i++) {
            prefijo.append(ALFABETO.charAt(aleatorio.nextInt(ALFABETO.length())));
        }
        byte[] secreto = new byte[BYTES_SECRETO];
        aleatorio.nextBytes(secreto);
        String key = prefijo + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secreto);
        return new KeyGenerada(prefijo.toString(), key, hash(key));
    }

    /** Parte no secreta de la key, o vacío si no tiene el formato esperado. */
    public Optional<String> prefijoDe(String key) {
        if (key == null || !FORMATO.matcher(key).matches()) {
            return Optional.empty();
        }
        return Optional.of(key.substring(0, INICIO.length() + LARGO_IDENTIFICADOR));
    }

    public byte[] hash(String key) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    /** Comparación en tiempo constante: no revela por el tiempo de respuesta cuántos bytes coinciden. */
    public boolean coincide(String key, byte[] hashGuardado) {
        return MessageDigest.isEqual(hash(key), hashGuardado);
    }
}
