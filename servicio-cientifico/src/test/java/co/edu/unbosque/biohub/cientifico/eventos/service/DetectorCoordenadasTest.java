package co.edu.unbosque.biohub.cientifico.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SEC-J-13: detector de coordenadas del publicador (claves y texto libre)")
class DetectorCoordenadasTest {

    @Test
    void reconoceFormatosHabituales() {
        for (String t : List.of("colectado en 5.53214, -73.36712", "5°31'55\"N 73°22'02\"W", "4.6097 -74.0817",
                "5.5321 N, 73.3671 W", "lat: 5.53 lon: -73.36", "73° 22′ W")) {
            assertThat(DetectorCoordenadas.pareceCoordenada(t)).as(t).isTrue();
        }
    }

    @Test
    void noMarcaTextoBiologicoHabitual() {
        for (String t : List.of("Anfibio endémico de páramo", "medía 12,5 cm y pesaba 3,2 g", "2026-03-14",
                "altitud entre 3.200, 3.500 msnm", "longitud del cuerpo: 12 mm", "lat", "")) {
            assertThat(DetectorCoordenadas.pareceCoordenada(t)).as(t).isFalse();
        }
        assertThat(DetectorCoordenadas.pareceCoordenada(null)).isFalse();
    }

    @Test
    void recorreEstructurasAnidadas() {
        assertThat(DetectorCoordenadas.contiene(Map.of("a", Map.of("b", List.of(Map.of("Latitud", 1)))))).isTrue();
        assertThat(DetectorCoordenadas.contiene(Map.of("x", List.of("ok", "5°31'55\"N")))).isTrue();
        assertThat(DetectorCoordenadas.contiene(Map.of("descripcion", "Anfibio de páramo", "version", 2))).isFalse();
        assertThat(DetectorCoordenadas.contiene(null)).isFalse();
    }

    @Test
    void unaEstructuraDemasiadoHondaEsSospechosa() {
        Object nodo = "x";
        for (int i = 0; i < 100; i++) {
            nodo = Map.of("k", nodo);
        }
        assertThat(DetectorCoordenadas.contiene(nodo)).isTrue();
    }
}
