package co.edu.unbosque.biohub.consultapublica.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.consultapublica.domain.EstadoConservacion;
import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.service.ProyectorFichas;
import co.edu.unbosque.biohub.consultapublica.soporte.BaseIntegracion;

/** D14: ninguna respuesta pública (éxito ni error) contiene claves de coordenadas, ni siquiera anidadas. */
@Testcontainers(disabledWithoutDocker = true)
class SinCoordenadasIntegracionTest extends BaseIntegracion {

    static final Set<String> PROHIBIDAS = Set.of("lat", "lon", "lng", "latitud", "longitud", "latitude", "longitude",
            "coordenadas", "coordinates", "geometry", "geom", "ubicacionexacta");

    @Autowired
    private ProyectorFichas proyector;
    @Autowired
    private ObjectMapper mapper;

    private static void claves(JsonNode nodo, List<String> destino) {
        if (nodo.isObject()) {
            nodo.fields().forEachRemaining(e -> {
                destino.add(e.getKey().toLowerCase(Locale.ROOT));
                claves(e.getValue(), destino);
            });
        } else if (nodo.isArray()) {
            nodo.forEach(n -> claves(n, destino));
        }
    }

    private void verificar(String url, String... parametros) throws Exception {
        var peticion = get(url);
        for (int i = 0; i < parametros.length; i += 2) {
            peticion.param(parametros[i], parametros[i + 1]);
        }
        String cuerpo = mvc.perform(peticion).andReturn().getResponse().getContentAsString();
        List<String> claves = new ArrayList<>();
        claves(mapper.readTree(cuerpo), claves);
        assertThat(claves).as("claves de %s", url).isNotEmpty().doesNotContainAnyElementsOf(PROHIBIDAS);
    }

    @Test
    void ningunaRespuestaPublicaContieneClavesDeCoordenadas() throws Exception {
        UUID id = id("sin-coords");
        proyector.aplicar(new FichaPublica(id, 1, "Atelopus ebenoides", "Rana arlequín", "Bufonidae", "Adulto",
                LocalDate.of(2026, 3, 14), EstadoConservacion.EN_PELIGRO, "15", "Boyacá", "Andina", "Texto"));

        verificar("/api/publica/fichas");
        verificar("/api/publica/fichas", "q", "atelopus", "departamento", "15", "familia", "Bufonidae");
        verificar("/api/publica/fichas", "incluirCoordenadas", "true", "lat", "5", "lon", "-73");
        verificar("/api/publica/fichas/" + id);
        verificar("/api/publica/fichas/" + id, "incluirCoordenadas", "true");
        verificar("/api/publica/fichas/" + UUID.randomUUID());      // 404
        verificar("/api/publica/fichas/no-uuid");                  // 400
        verificar("/api/publica/fichas", "size", "999");           // 400
        verificar("/api/publica/fichas", "departamento", "Atlantis");
    }
}
