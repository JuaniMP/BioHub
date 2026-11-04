// Solo CI / pruebas locales de Bruno. NUNCA es una migracion ni se empaqueta en los servicios.
//
// Genera por la salida estandar el SQL que siembra 3 filas en cientifico.ubicacion_hallazgo con las
// coordenadas cifradas EXACTAMENTE como lo hace el servicio (AES-256-GCM, plano = 2 doubles big-endian,
// nonce de 12 bytes, AAD = UUID canonico en minusculas), para poder probar /ubicacion/mapa de punta a punta
// sin el modulo de registro (que publica/llama a UbicacionService).
//
// Uso:  java infra/ci/SembrarHallazgos.java <claveBase64> <versionClave> | mysql ... cientifico
// La clave debe ser la misma de UBICACION_CLAVES del servicio (en CI es una clave ficticia).
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

public class SembrarHallazgos {

    // Mismos ids que bruno/environments/*.bru (variables hallazgoA/B/C)
    record H(String id, String especie, String fecha, String inv, String depto, String estado, Double lat, Double lon) {}

    static final H[] HALLAZGOS = {
        new H("3f2a9c1e-5b7d-4e8a-9f10-2c3d4e5f6a7b", "Atelopus ebenoides", "2026-03-14", "investigador1", "15", "EN_PELIGRO", 5.1234567, -73.1234567),
        new H("8b1c2d3e-4f50-4a6b-8c7d-9e0f1a2b3c4d", "Atelopus muisca", "2026-02-02", "investigador2", "25", "EN_PELIGRO", 4.7654321, -74.0654321),
        new H("c0ffee00-1111-4222-8333-444455556666", "Espeletia grandiflora", "2026-01-20", "investigador1", "15", "NO_AMENAZADA", null, null),
    };

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Uso: java SembrarHallazgos.java <claveBase64> <versionClave>");
            System.exit(2);
        }
        byte[] clave = Base64.getDecoder().decode(args[0]);
        if (clave.length != 32) throw new IllegalArgumentException("La clave debe tener 32 bytes");
        String version = args[1];
        if (!version.matches("[A-Za-z0-9_-]{1,16}")) throw new IllegalArgumentException("version de clave invalida");
        SecureRandom azar = new SecureRandom();

        for (H h : HALLAZGOS) {
            String cols = "hallazgo_id, version, departamento_codigo, nombre_cientifico, fecha_recoleccion, investigador, estado_conservacion";
            String vals = "UNHEX(REPLACE('" + h.id() + "','-','')), 1, '" + h.depto() + "', '" + h.especie() + "', '"
                    + h.fecha() + "', '" + h.inv() + "', '" + h.estado() + "'";
            if (h.lat() != null) {
                byte[] plano = ByteBuffer.allocate(16).putDouble(h.lat()).putDouble(h.lon()).array();
                byte[] nonce = new byte[12];
                azar.nextBytes(nonce);
                Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
                c.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(clave, "AES"), new GCMParameterSpec(128, nonce));
                c.updateAAD(h.id().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8));
                byte[] cifrado = c.doFinal(plano);
                cols += ", coord_cifrada, coord_nonce, clave_version, cifrado_en";
                vals += ", UNHEX('" + hex(cifrado) + "'), UNHEX('" + hex(nonce) + "'), '" + version + "', NOW(3)";
            }
            // Idempotente: repetir la siembra no duplica ni falla
            System.out.println("INSERT IGNORE INTO ubicacion_hallazgo (" + cols + ") VALUES (" + vals + ");");
        }
    }

    static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }
}
