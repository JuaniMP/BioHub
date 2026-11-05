package co.edu.unbosque.biohub.cientifico.registro.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import co.edu.unbosque.biohub.cientifico.ubicacion.service.EstadoConservacion;

/**
 * Equivalencia UICN acordada en 04-contrato-api §6 (obligación 5 de registro). Si alguien la cambia,
 * cambia qué especies el portal considera en peligro y qué coordenadas se cifran: por eso se fija aquí.
 */
class CategoriaUicnTest {

    @ParameterizedTest
    @CsvSource({
            "CR, EN_PELIGRO",
            "EN, EN_PELIGRO",
            "VU, NO_AMENAZADA",
            "NT, NO_AMENAZADA",
            "LC, NO_AMENAZADA",
            "DD, SIN_EVALUAR",
            "NE, SIN_EVALUAR"
    })
    @DisplayName("cada categoria UICN se traduce al estado acordado con ubicacion")
    void traduceCategorias(CategoriaUicn categoria, EstadoConservacion esperado) {
        assertThat(categoria.estadoConservacion()).isEqualTo(esperado);
    }
}
