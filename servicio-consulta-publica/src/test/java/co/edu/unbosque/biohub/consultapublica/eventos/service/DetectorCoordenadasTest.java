package co.edu.unbosque.biohub.consultapublica.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@DisplayName("SEC-J-02 / SEC-J-03: detector de coordenadas en claves y texto libre (RF11, RNF9, ASR6)")
class DetectorCoordenadasTest {

    @Test
    @DisplayName("SEC-J-02: reconoce coordenadas escritas en texto libre en los formatos habituales")
    void reconoceFormatosHabituales() {
        for (String t : List.of(
                "colectado en 5.53214, -73.36712",           // ejemplo del reporte de seguridad
                "5°31'55\"N 73°22'02\"W",                      // ejemplo del reporte de seguridad
                "punto 5,53214;-73,36712 cerca del río",
                "4.6097 -74.0817",
                "4.60971/-74.08175",
                "5.5321 N, 73.3671 W",
                "5.53214°N 73.36712°W",
                "lat: 5.53 lon: -73.36",
                "Lat=5.5 Lng=-73.3",
                "latitud 5.53 longitud -73.36",
                "5º 31.9' N",
                "73° 22′ W",
                "ubicado en geo:5.53214,-73.36712 aprox",
                "https://maps.google.com/?q=5.53214,-73.36712",
                "coordenadas 5.53214 , −73.36712",         // menos tipográfico
                "\n5.53214,\n-73.36712\n")) {
            assertThat(DetectorCoordenadas.pareceCoordenada(t)).as(t).isTrue();
        }
    }

    @Test
    @DisplayName("SEC-J-02: no marca como coordenada el texto biológico habitual (falsos positivos razonables)")
    void noMarcaTextoBiologicoHabitual() {
        for (String t : List.of(
                "Anfibio endémico de páramo",
                "Atelopus ebenoides",
                "Rana arlequín de Boyacá",
                "medía 12,5 cm y pesaba 3,2 g",
                "recolectado el 2026-03-14 a las 10:30",
                "14/03/2026",
                "altitud entre 3.200, 3.500 msnm",           // miles con punto, no una coordenada
                "3.200 msnm",
                "población de 1.500 individuos",
                "temperatura 12.5 grados, humedad 80 %",
                "ángulo de 45° respecto al suelo",
                "longitud del cuerpo: 12 mm",                // 'longitud' también significa largo
                "a 2.800 m, 3.100 m y 3.400 m de altitud",
                "Parque Nacional Natural Chingaza, sector Monterredondo",
                "lat",                                         // palabra suelta sin número
                "talla 5.2, peso 3.1",
                "")) {
            assertThat(DetectorCoordenadas.pareceCoordenada(t)).as(t).isFalse();
        }
        assertThat(DetectorCoordenadas.pareceCoordenada(null)).isFalse();
    }

    @Test
    @DisplayName("SEC-J-03: detecta claves de coordenadas en cualquier nivel y capitalización, y texto en listas")
    void recorreEstructuras() {
        assertThat(DetectorCoordenadas.contiene(Map.of("a", Map.of("b", List.of(Map.of("Latitud", 1)))))).isTrue();
        assertThat(DetectorCoordenadas.contiene(Map.of("descripcion", "ver 5.53214, -73.36712"))).isTrue();
        assertThat(DetectorCoordenadas.contiene(Map.of("x", List.of("ok", "5°31'55\"N")))).isTrue();
        assertThat(DetectorCoordenadas.contiene(Map.of("descripcion", "Anfibio de páramo", "version", 2))).isFalse();
        assertThat(DetectorCoordenadas.contiene(null)).isFalse();
    }

    @Test
    @DisplayName("SEC-J-03: una estructura con profundidad desmesurada se trata como sospechosa, no desborda la pila")
    void estructuraDemasiadoHonda() {
        Object nodo = "x";
        for (int i = 0; i < 5_000; i++) {
            nodo = List.of(nodo);
        }
        assertThat(DetectorCoordenadas.contiene(nodo)).isTrue();
    }

    @Test
    @DisplayName("SEC-J-03: en un cuerpo ilegible detecta coordenadas tras deshacer escapes JSON")
    void cuerpoEnBruto() {
        assertThat(DetectorCoordenadas.contieneEnBruto("{\"descripcion\": \"5\\u00b031'55\\\"N\" ".getBytes(StandardCharsets.UTF_8)))
                .as("grados con escape unicode y comilla escapada").isTrue();
        assertThat(DetectorCoordenadas.contieneEnBruto("{\"latitud\": 5.5, \"roto".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(DetectorCoordenadas.contieneEnBruto("{'Lat' : 5".getBytes(StandardCharsets.UTF_8))).isTrue();
        assertThat(DetectorCoordenadas.contieneEnBruto("{\"id\": ".getBytes(StandardCharsets.UTF_8))).isFalse();
        assertThat(DetectorCoordenadas.contieneEnBruto(new byte[] {(byte) 0xff, (byte) 0xfe, 0x00, (byte) 0xc3, 0x28})).isFalse();
        assertThat(DetectorCoordenadas.contieneEnBruto(new byte[0])).isFalse();
        assertThat(DetectorCoordenadas.contieneEnBruto(null)).isFalse();
    }

    @Test
    @Timeout(value = 5)
    @DisplayName("SEC-J-02: texto hostil muy largo no provoca retrocesos catastróficos (cuantificadores acotados)")
    void sinRetrocesosCatastroficos() {
        String espacios = "5.5321 " + " ".repeat(200_000) + "x";
        String digitos = "1".repeat(200_000) + ".";
        String mezcla = "5.53214,".repeat(20_000);
        String grados = "5°".repeat(50_000) + " ";
        for (String t : List.of(espacios, digitos, mezcla, grados)) {
            org.junit.jupiter.api.Assertions.assertTimeout(Duration.ofSeconds(3), () -> DetectorCoordenadas.pareceCoordenada(t));
        }
    }

    @Test
    @DisplayName("SEC-J-03: solo analiza hasta el máximo de bytes en bruto")
    void limiteDeAnalisisEnBruto() {
        byte[] enorme = ("x".repeat(DetectorCoordenadas.MAXIMO_BYTES_ANALIZADOS + 10) + " 5.53214, -73.36712")
                .getBytes(StandardCharsets.UTF_8);
        assertThat(DetectorCoordenadas.contieneEnBruto(enorme)).isFalse(); // fuera de la ventana analizada (límite documentado)
    }
}
