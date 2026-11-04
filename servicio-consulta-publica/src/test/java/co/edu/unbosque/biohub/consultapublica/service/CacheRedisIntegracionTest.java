package co.edu.unbosque.biohub.consultapublica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.biohub.consultapublica.domain.EstadoConservacion;
import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.soporte.BaseIntegracion;

/** Caché Redis real: hit, miss, TTL, invalidación y que los errores no se cachean. */
@Testcontainers(disabledWithoutDocker = true)
class CacheRedisIntegracionTest extends BaseIntegracion {

    @Autowired
    private ProyectorFichas proyector;
    @Autowired
    private InvalidadorCacheFichas invalidador;
    @Autowired
    private StringRedisTemplate redis;

    private UUID crear(String semilla, String nombre) {
        UUID id = id(semilla);
        proyector.aplicar(new FichaPublica(id, 1, nombre, "Rana", "Bufonidae", null, LocalDate.of(2026, 3, 14),
                EstadoConservacion.NO_AMENAZADA, "15", "Boyacá", "Andina", null));
        return id;
    }

    private Set<String> claves(String patron) {
        return redis.keys("biohub:publica:" + patron);
    }

    @Test
    void laFichaSeCacheaEnRedisConTtlDe5MinutosYSeSirveDesdeLaCache() throws Exception {
        UUID id = crear("cache1", "Atelopus uno");
        assertThat(claves("fichas::*")).isEmpty(); // miss: aún no hay entrada

        mvc.perform(get("/api/publica/fichas/" + id)).andExpect(status().isOk());
        String clave = "biohub:publica:fichas::" + id;
        assertThat(redis.hasKey(clave)).isTrue();
        assertThat(redis.getExpire(clave)).isBetween(200L, 300L);

        // Cambio directo en MySQL (sin evento): mientras haya caché, se sigue sirviendo el valor anterior (hit)
        jdbc.sql("UPDATE ficha_publica SET nombre_cientifico = 'Cambiado en BD' WHERE hallazgo_id = UNHEX(REPLACE(?, '-', ''))")
                .param(id.toString()).update();
        mvc.perform(get("/api/publica/fichas/" + id))
                .andExpect(jsonPath("$.nombreCientifico").value("Atelopus uno"));

        // Al invalidar (lo que hace el consumidor tras el commit) se vuelve a leer de MySQL
        invalidador.invalidar(id);
        assertThat(redis.hasKey(clave)).isFalse();
        mvc.perform(get("/api/publica/fichas/" + id))
                .andExpect(jsonPath("$.nombreCientifico").value("Cambiado en BD"));
    }

    @Test
    void laBusquedaSeCacheaPor60SegundosYSeVaciaTrasUnEvento() throws Exception {
        UUID id = crear("cache2", "Atelopus dos");
        mvc.perform(get("/api/publica/fichas").param("q", "atelopus")).andExpect(jsonPath("$.total").value(1));
        Set<String> claves = claves("busquedas::*");
        assertThat(claves).hasSize(1);
        assertThat(redis.getExpire(claves.iterator().next())).isBetween(40L, 60L);

        // Otra consulta (otros parámetros) genera otra clave; "ATELOPUS " con tildes/mayúsculas comparte la misma
        mvc.perform(get("/api/publica/fichas").param("q", " ATELOPUS ")).andExpect(jsonPath("$.total").value(1));
        assertThat(claves("busquedas::*")).hasSize(1);
        mvc.perform(get("/api/publica/fichas").param("q", "atelopus").param("page", "1"));
        assertThat(claves("busquedas::*")).hasSize(2);

        invalidador.invalidar(id);
        assertThat(claves("busquedas::*")).isEmpty();
    }

    @Test
    void claveConCaracteresDeSeparadorNoColisionaConOtrosFiltros() throws Exception {
        crear("cache3", "Atelopus tres");
        mvc.perform(get("/api/publica/fichas").param("q", "atelopus|f=bufonidae")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/publica/fichas").param("q", "atelopus").param("familia", "bufonidae"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void los404YLos400NoSeCachean() throws Exception {
        mvc.perform(get("/api/publica/fichas/" + UUID.randomUUID())).andExpect(status().isNotFound());
        mvc.perform(get("/api/publica/fichas").param("size", "999")).andExpect(status().isBadRequest());
        assertThat(claves("*")).isEmpty();

        // Si luego aparece la ficha, se encuentra de inmediato (no quedó un 404 cacheado)
        UUID id = crear("cache4", "Atelopus cuatro");
        mvc.perform(get("/api/publica/fichas/" + id)).andExpect(status().isOk());
    }

    @Test
    void elValorCacheadoNoContieneClavesDeCoordenadas() throws Exception {
        UUID id = crear("cache5", "Atelopus cinco");
        mvc.perform(get("/api/publica/fichas/" + id));
        mvc.perform(get("/api/publica/fichas").param("q", "atelopus"));
        for (String clave : claves("*")) {
            String json = redis.opsForValue().get(clave).toLowerCase();
            assertThat(json).doesNotContain("latitud", "longitud", "\"lat\"", "\"lon\"", "coordenadas");
        }
    }
}
