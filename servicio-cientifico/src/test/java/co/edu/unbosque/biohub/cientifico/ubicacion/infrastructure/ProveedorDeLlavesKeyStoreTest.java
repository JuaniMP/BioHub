package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.ProveedorDeLlaves;

/**
 * ARQ-11 (ASR6, ambigüedad A2): segundo adaptador del puerto {@code ProveedorDeLlaves}, sobre un PKCS12. El .p12
 * se genera aquí en un directorio temporal: nunca se versiona uno.
 */
class ProveedorDeLlavesKeyStoreTest {

    private static final char[] CLAVE = "contrasena-de-prueba".toCharArray();
    private static final SecureRandom ALEATORIO = new SecureRandom();

    @TempDir
    Path tmp;

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        ALEATORIO.nextBytes(b);
        return b;
    }

    private Path p12(Map<String, byte[]> llaves, char[] contrasena) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        for (var e : llaves.entrySet()) {
            ks.setEntry(e.getKey(), new KeyStore.SecretKeyEntry(new SecretKeySpec(e.getValue(), "AES")),
                    new KeyStore.PasswordProtection(contrasena));
        }
        Path ruta = tmp.resolve(UUID.randomUUID() + ".p12");
        try (OutputStream out = Files.newOutputStream(ruta)) {
            ks.store(out, contrasena);
        }
        restringir(ruta); // SEC-J-12: el adaptador exige chmod 600; Files.newOutputStream deja 644 con umask 022
        return ruta;
    }

    private static void restringir(Path ruta) throws Exception {
        if (ruta.getFileSystem().supportedFileAttributeViews().contains("posix")) {
            Files.setPosixFilePermissions(ruta, PosixFilePermissions.fromString("rw-------"));
        }
    }

    @Test
    @DisplayName("ARQ-11: carga las llaves versionadas del PKCS12 y la activa viene de UBICACION_CLAVE_ACTIVA")
    void cargaLlavesVersionadas() throws Exception {
        byte[] k1 = bytes(32);
        byte[] k2 = bytes(32);
        ProveedorDeLlaves p = new ProveedorDeLlavesKeyStore(p12(Map.of("v1", k1, "v2", k2), CLAVE), CLAVE, "v2");
        assertThat(p.versionActiva()).isEqualTo("v2");
        assertThat(p.versiones()).containsExactlyInAnyOrder("v1", "v2");
        assertThat(p.llave("v1")).isPresent().get().satisfies(k -> {
            assertThat(k.getEncoded()).isEqualTo(k1);
            assertThat(k.getAlgorithm()).isEqualTo("AES");
        });
        assertThat(p.llave("v3")).isEmpty();
        assertThat(p.llave(null)).isEmpty();
    }

    @Test
    @DisplayName("ARQ-11 ASR6: con el adaptador de keystore el cifrador funciona igual y es interoperable con el proveedor de entorno")
    void interoperaConElProveedorDeEntorno() throws Exception {
        byte[] k1 = bytes(32);
        ProveedorDeLlaves ks = new ProveedorDeLlavesKeyStore(p12(Map.of("v1", k1), CLAVE), CLAVE, "v1");
        ProveedorDeLlaves env = new ProveedorDeLlavesEntorno("v1:" + Base64.getEncoder().encodeToString(k1), "v1");
        UUID id = UUID.randomUUID();
        var cifradas = new CifradorAesGcm(ks).cifrar(id, new Coordenadas(5.5, -73.5));
        assertThat(cifradas.claveVersion()).isEqualTo("v1");
        assertThat(new CifradorAesGcm(env).descifrar(id, cifradas)).isEqualTo(new Coordenadas(5.5, -73.5));
    }

    @Test
    @DisplayName("ARQ-11: contraseña incorrecta, archivo inexistente o contraseña vacía impiden arrancar sin revelar nada")
    void noArrancaConContrasenaORutaIncorrectas() throws Exception {
        Path ruta = p12(Map.of("v1", bytes(32)), CLAVE);
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(ruta, "otra".toCharArray(), "v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageNotContaining("otra").hasMessageNotContaining(ruta.toString()).hasNoCause();
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(tmp.resolve("no-existe.p12"), CLAVE, "v1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UBICACION_KEYSTORE_RUTA");
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(null, CLAVE, "v1")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(ruta, new char[0], "v1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UBICACION_KEYSTORE_PASSWORD");
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(ruta, null, "v1")).isInstanceOf(IllegalStateException.class);
        Path noEsP12 = tmp.resolve("basura.p12");
        Files.writeString(noEsP12, "esto no es un keystore");
        restringir(noEsP12);
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(noEsP12, CLAVE, "v1")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ARQ-11: sin llave activa válida (vacía, desconocida o sin alias) el servicio no arranca")
    void exigeLlaveActivaValida() throws Exception {
        Path ruta = p12(Map.of("v1", bytes(32)), CLAVE);
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(ruta, CLAVE, null)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UBICACION_CLAVE_ACTIVA");
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(ruta, CLAVE, " ")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(ruta, CLAVE, "v2")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("ARQ-11: rechaza llaves que no son AES de 32 bytes y alias que no son vN; un keystore sin llaves no sirve")
    void validaLlavesYAlias() throws Exception {
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(p12(Map.of("v1", bytes(16)), CLAVE), CLAVE, "v1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("32 bytes");
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(p12(Map.of("v1", bytes(32), "principal", bytes(32)), CLAVE), CLAVE, "v1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("alias vN");
        assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(p12(Map.of(), CLAVE), CLAVE, "v1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no contiene llaves");
    }

    @Test
    @DisplayName("ARQ-11 ASR6: toString no muestra material de llaves ni contraseña")
    void toStringNoFiltra() throws Exception {
        byte[] k = bytes(32);
        String texto = new ProveedorDeLlavesKeyStore(p12(Map.of("v1", k), CLAVE), CLAVE, "v1").toString();
        assertThat(texto).doesNotContain(Base64.getEncoder().encodeToString(k)).doesNotContain(new String(CLAVE)).contains("v1");
        assertThat(new PropiedadesCifrado("v1:AAAA", "v1", false, "keystore", "/x.p12", "secreto-123").toString())
                .doesNotContain("secreto-123").doesNotContain("AAAA");
    }

    @Test
    @DisplayName("ARQ-11: ConfiguracionUbicacion elige el adaptador por propiedad; por defecto es el de entorno")
    void laPropiedadEligeElAdaptador() throws Exception {
        byte[] k = bytes(32);
        Path ruta = p12(Map.of("v1", k), CLAVE);
        ConfiguracionUbicacion cfg = new ConfiguracionUbicacion();
        MockEnvironment entorno = new MockEnvironment();

        ProveedorDeLlaves porDefecto = cfg.proveedorDeLlaves(
                new PropiedadesCifrado("v1:" + Base64.getEncoder().encodeToString(k), "v1", false, null, null, null), entorno);
        assertThat(porDefecto).isInstanceOf(ProveedorDeLlavesEntorno.class);

        ProveedorDeLlaves keystore = cfg.proveedorDeLlaves(
                new PropiedadesCifrado("", "v1", false, "KeyStore", ruta.toString(), new String(CLAVE)), entorno);
        assertThat(keystore).isInstanceOf(ProveedorDeLlavesKeyStore.class);
        assertThat(keystore.llave("v1")).isPresent();

        assertThatThrownBy(() -> cfg.proveedorDeLlaves(
                new PropiedadesCifrado("", "v1", false, "keystore", " ", new String(CLAVE)), entorno))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("UBICACION_KEYSTORE_RUTA");
        assertThatThrownBy(() -> cfg.proveedorDeLlaves(
                new PropiedadesCifrado("", "v1", false, "kms", null, null), entorno))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'entorno' o 'keystore'");
    }

    // ---- SEC-J-12: permisos del archivo ----

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("SEC-J-12: un .p12 con permisos 600 se acepta")
    void aceptaPermisos600() throws Exception {
        Path ruta = p12(Map.of("v1", bytes(32)), CLAVE);
        assertThat(Files.getPosixFilePermissions(ruta)).containsExactlyInAnyOrder(
                PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

        assertThat(new ProveedorDeLlavesKeyStore(ruta, CLAVE, "v1").versionActiva()).isEqualTo("v1");
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("SEC-J-12: un .p12 legible por grupo u otros (644, 640, 604) no arranca y el mensaje no revela la ruta")
    void rechazaPermisosAbiertos() throws Exception {
        for (String modo : new String[] {"rw-r--r--", "rw-r-----", "rw----r--", "rw-rw----"}) {
            Path ruta = p12(Map.of("v1", bytes(32)), CLAVE);
            Files.setPosixFilePermissions(ruta, PosixFilePermissions.fromString(modo));
            assertThatThrownBy(() -> new ProveedorDeLlavesKeyStore(ruta, CLAVE, "v1")).as(modo)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("chmod 600").hasMessageContaining("UBICACION_KEYSTORE_RUTA")
                    .hasMessageNotContaining(ruta.toString()).hasMessageNotContaining(tmp.toString()).hasNoCause();
        }
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    @DisplayName("SEC-J-12: ConfiguracionUbicacion propaga el rechazo (fail-closed) con un .p12 644")
    void laConfiguracionRechazaUnP12Abierto() throws Exception {
        Path ruta = p12(Map.of("v1", bytes(32)), CLAVE);
        Files.setPosixFilePermissions(ruta, PosixFilePermissions.fromString("rw-r--r--"));

        assertThatThrownBy(() -> new ConfiguracionUbicacion().proveedorDeLlaves(
                new PropiedadesCifrado("", "v1", false, "keystore", ruta.toString(), new String(CLAVE)),
                new MockEnvironment())).isInstanceOf(IllegalStateException.class).hasMessageContaining("chmod 600");
    }
}
