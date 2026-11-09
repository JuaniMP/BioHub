package co.edu.unbosque.biohub.cientifico.eventos.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

@DisplayName("SEC-J-13: HallazgoRegistrado no serializa las coordenadas (D14, RNF9)")
class HallazgoRegistradoSerializacionTest {

    private final ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private HallazgoRegistrado evento() {
        return new HallazgoRegistrado(UUID.randomUUID(), 1, "Atelopus ebenoides", LocalDate.of(2026, 3, 14),
                "investigador1", "15", "EN_PELIGRO", 5.53214, -73.36712, Instant.parse("2026-03-14T10:00:00Z"));
    }

    @Test
    @DisplayName("writeValueAsString no incluye latitud ni longitud ni sus valores")
    void jsonSinCoordenadas() throws Exception {
        String json = mapper.writeValueAsString(evento());

        assertThat(json).doesNotContain("latitud").doesNotContain("longitud")
                .doesNotContain("5.53214").doesNotContain("73.36712");
        assertThat(json).contains("nombreCientifico", "estadoConservacion");
    }

    @Test
    @DisplayName("convertValue(evento, Map) (la forma natural de armar 'datos') tampoco las incluye")
    @SuppressWarnings("unchecked")
    void convertValueSinCoordenadas() {
        Map<String, Object> mapa = mapper.convertValue(evento(), Map.class);

        assertThat(mapa).doesNotContainKeys("latitud", "longitud");
        assertThat(mapa.toString()).doesNotContain("5.53214").doesNotContain("73.36712");
    }

    @Test
    @DisplayName("las coordenadas siguen disponibles en memoria para ubicacion (solo se ocultan al serializar)")
    void siguenEnMemoria() {
        HallazgoRegistrado e = evento();

        assertThat(e.latitud()).isEqualTo(5.53214);
        assertThat(e.longitud()).isEqualTo(-73.36712);
        assertThat(e.toString()).doesNotContain("5.53214");
    }

    @Test
    @DisplayName("HallazgoReclasificado no tiene coordenadas que serializar")
    void reclasificadoSinCoordenadas() throws Exception {
        String json = mapper.writeValueAsString(new HallazgoReclasificado(UUID.randomUUID(), 2,
                "Atelopus ebenoides", LocalDate.of(2026, 3, 14), "15", "EN_PELIGRO", Instant.now()));

        assertThat(json).doesNotContain("latitud").doesNotContain("longitud");
    }
}
