package co.edu.unbosque.biohub.cientifico.registro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SEC-J-02: de nada sirve cifrar las coordenadas si alguien las escribe en la descripción. Pero una
 * regla demasiado estricta bloquearía texto científico legítimo, así que ambos lados importan.
 */
class ValidadorTextoPublicoTest {

    private final ValidadorTextoPublico validador = new ValidadorTextoPublico();

    @ParameterizedTest
    @ValueSource(strings = {
            "Visto en 5.53214, -73.36712 cerca del rio",
            "5.53214,-73.36712",
            "Coordenada 5°31'55\"N",
            "73° 22' 10\" W",
            "Ubicado a 5.53° N",
            "El punto exacto es 4.6097100, -74.0817500"
    })
    @DisplayName("detecta coordenadas escritas en texto")
    void detectaCoordenadas(String texto) {
        assertThat(validador.contieneCoordenadas(texto)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Recolectado el 2026-03-14 en expedicion",
            "Ejemplar de 12,5 cm de longitud",
            "Habita entre 3.200 y 3.500 msnm",
            "Temperatura del agua: 12.5 °C",
            "Anfibio endemico de paramo, piel negra con manchas",
            "Se observaron 1.250 individuos en la zona",
            "Atelopus ebenoides",
            "Bufonidae"
    })
    @DisplayName("no confunde fechas, medidas ni altitudes con coordenadas")
    void noConfundeTextoLegitimo(String texto) {
        assertThat(validador.contieneCoordenadas(texto)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("un texto vacio o ausente no es un problema")
    void textoVacio(String texto) {
        assertThat(validador.contieneCoordenadas(texto)).isFalse();
    }

    @Test
    @DisplayName("verificar informa el campo que falla, sin repetir el valor")
    void verificarInformaElCampo() {
        assertThatThrownBy(() -> validador.verificar("descripcion", "Visto en 5.53214, -73.36712"))
                .isInstanceOf(DatosHallazgoInvalidosException.class)
                .hasMessage("no puede contener coordenadas")
                .extracting(e -> ((DatosHallazgoInvalidosException) e).campo())
                .isEqualTo("descripcion");
    }

    @Test
    @DisplayName("el mensaje de error nunca incluye la coordenada detectada")
    void elMensajeNoFiltraLaCoordenada() {
        assertThatThrownBy(() -> validador.verificar("descripcion", "Visto en 5.53214, -73.36712"))
                .hasMessageNotContaining("5.53214")
                .hasMessageNotContaining("73.36712");
    }
}
