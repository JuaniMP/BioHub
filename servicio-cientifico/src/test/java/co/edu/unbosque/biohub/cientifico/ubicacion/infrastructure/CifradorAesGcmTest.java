package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException.Motivo;

class CifradorAesGcmTest {

    static String llaveNueva() {
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private final String k1 = llaveNueva();
    private final String k2 = llaveNueva();
    private final Coordenadas coords = new Coordenadas(5.1234567, -73.1234567);
    private final UUID id = UUID.randomUUID();

    private CifradorAesGcm cifrador(String claves, String activa) {
        return new CifradorAesGcm(new ProveedorDeLlavesEntorno(claves, activa));
    }

    @Test
    void cifraYDescifraConLaLlaveActiva() {
        CifradorAesGcm c = cifrador("v1:" + k1, "v1");
        CoordenadasCifradas cif = c.cifrar(id, coords);
        assertThat(cif.claveVersion()).isEqualTo("v1");
        assertThat(cif.nonce()).hasSize(12);
        assertThat(cif.textoCifrado()).hasSize(32); // 16 B cifrados + 16 B de tag
        assertThat(c.descifrar(id, cif)).isEqualTo(coords);
    }

    @Test
    void elTextoCifradoNoContieneLasCoordenadasEnClaro() {
        CoordenadasCifradas cif = cifrador("v1:" + k1, "v1").cifrar(id, coords);
        byte[] plano = ByteBuffer.allocate(16).putDouble(coords.latitud()).putDouble(coords.longitud()).array();
        assertThat(Arrays.equals(Arrays.copyOf(cif.textoCifrado(), 16), plano)).isFalse();
    }

    @Test
    void cadaCifradoUsaUnNonceNuevo() {
        CifradorAesGcm c = cifrador("v1:" + k1, "v1");
        CoordenadasCifradas a = c.cifrar(id, coords);
        CoordenadasCifradas b = c.cifrar(id, coords);
        assertThat(a.nonce()).isNotEqualTo(b.nonce());
        assertThat(a.textoCifrado()).isNotEqualTo(b.textoCifrado());
    }

    @Test
    void copiarElCifradoAOtroHallazgoFallaPorElAad() {
        CifradorAesGcm c = cifrador("v1:" + k1, "v1");
        CoordenadasCifradas cif = c.cifrar(id, coords);
        assertThatThrownBy(() -> c.descifrar(UUID.randomUUID(), cif))
                .isInstanceOfSatisfying(CoordenadasNoDescifrablesException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.INTEGRIDAD));
    }

    @Test
    void unaAlteracionDelTextoOdelNonceSeDetecta() {
        CifradorAesGcm c = cifrador("v1:" + k1, "v1");
        CoordenadasCifradas cif = c.cifrar(id, coords);
        byte[] texto = cif.textoCifrado().clone();
        texto[3] ^= 0x01;
        byte[] nonce = cif.nonce().clone();
        nonce[0] ^= 0x01;
        assertThatThrownBy(() -> c.descifrar(id, new CoordenadasCifradas(texto, cif.nonce(), "v1")))
                .isInstanceOfSatisfying(CoordenadasNoDescifrablesException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.INTEGRIDAD));
        assertThatThrownBy(() -> c.descifrar(id, new CoordenadasCifradas(cif.textoCifrado(), nonce, "v1")))
                .isInstanceOf(CoordenadasNoDescifrablesException.class);
        assertThatThrownBy(() -> c.descifrar(id, new CoordenadasCifradas(cif.textoCifrado(), new byte[5], "v1")))
                .isInstanceOf(CoordenadasNoDescifrablesException.class);
    }

    @Test
    void cambiarLaClaveVersionAOtraLlaveFalla() {
        CifradorAesGcm c = cifrador("v1:" + k1 + ",v2:" + k2, "v1");
        CoordenadasCifradas cif = c.cifrar(id, coords);
        assertThatThrownBy(() -> c.descifrar(id, new CoordenadasCifradas(cif.textoCifrado(), cif.nonce(), "v2")))
                .isInstanceOfSatisfying(CoordenadasNoDescifrablesException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.INTEGRIDAD));
    }

    @Test
    void unaLlaveRetiradaSeReportaComoLlaveRetirada() {
        CoordenadasCifradas cif = cifrador("v1:" + k1, "v1").cifrar(id, coords);
        CifradorAesGcm soloV2 = cifrador("v2:" + k2, "v2");
        assertThatThrownBy(() -> soloV2.descifrar(id, cif))
                .isInstanceOfSatisfying(CoordenadasNoDescifrablesException.class,
                        e -> assertThat(e.motivo()).isEqualTo(Motivo.LLAVE_RETIRADA));
    }

    @Test
    void rotacionRecifrarPasaALaLlaveActivaConNonceNuevoYMismoValor() {
        CoordenadasCifradas v1 = cifrador("v1:" + k1, "v1").cifrar(id, coords);
        CifradorAesGcm c = cifrador("v1:" + k1 + ",v2:" + k2, "v2");
        assertThat(c.descifrar(id, v1)).isEqualTo(coords); // lo antiguo se sigue leyendo
        CoordenadasCifradas v2 = c.recifrar(id, v1);
        assertThat(v2.claveVersion()).isEqualTo("v2");
        assertThat(v2.nonce()).isNotEqualTo(v1.nonce());
        assertThat(c.descifrar(id, v2)).isEqualTo(coords);
        // con v1 retirada, lo re-cifrado sigue legible
        assertThat(cifrador("v2:" + k2, "v2").descifrar(id, v2)).isEqualTo(coords);
    }

    @Test
    void coordenadasYCifradasNoSeImprimen() {
        CoordenadasCifradas cif = cifrador("v1:" + k1, "v1").cifrar(id, coords);
        assertThat(coords.toString()).isEqualTo("Coordenadas[***]").doesNotContain("5.12");
        assertThat(cif.toString()).doesNotContain(Arrays.toString(cif.textoCifrado()));
    }

    @Test
    void coordenadasInvalidasSeRechazan() {
        assertThatThrownBy(() -> new Coordenadas(Double.NaN, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordenadas(0, Double.POSITIVE_INFINITY)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordenadas(91, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Coordenadas(0, -180.1)).isInstanceOf(IllegalArgumentException.class);
    }
}
