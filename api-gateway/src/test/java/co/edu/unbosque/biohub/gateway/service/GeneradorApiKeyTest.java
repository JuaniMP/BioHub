package co.edu.unbosque.biohub.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GeneradorApiKeyTest {

    private final GeneradorApiKey generador = new GeneradorApiKey();

    @Test
    void generaUnaKeyConElFormatoEsperado() {
        GeneradorApiKey.KeyGenerada generada = generador.generar();

        assertThat(generada.key()).matches("^bh_pub_[a-z0-9]{8}_[A-Za-z0-9_-]{43}$");
        assertThat(generada.prefijo()).hasSize(15);
        assertThat(generada.key()).startsWith(generada.prefijo() + "_");
        assertThat(generada.hash()).hasSize(32);
    }

    @Test
    void cadaKeyEsDistinta() {
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            keys.add(generador.generar().key());
        }
        assertThat(keys).hasSize(1_000);
    }

    @Test
    void elToStringNoRevelaLaKey() {
        GeneradorApiKey.KeyGenerada generada = generador.generar();

        assertThat(generada.toString()).doesNotContain(generada.key());
    }

    @Test
    void extraeElPrefijoDeUnaKeyBienFormada() {
        GeneradorApiKey.KeyGenerada generada = generador.generar();

        assertThat(generador.prefijoDe(generada.key())).contains(generada.prefijo());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"inventada", "bh_pub_corta", "otro_abcd2345_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"})
    void rechazaFormatosInvalidos(String key) {
        assertThat(generador.prefijoDe(key)).isEmpty();
    }

    @Test
    void reconoceLaKeyCorrectaYRechazaOtra() {
        GeneradorApiKey.KeyGenerada generada = generador.generar();

        assertThat(generador.coincide(generada.key(), generada.hash())).isTrue();
        assertThat(generador.coincide(generada.key() + "x", generada.hash())).isFalse();
    }
}
