package co.edu.unbosque.biohub.consultapublica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ARQ-06 (SAD §6.5, ASR1, RNF1): Strategy de búsqueda completa/degradada y su elección por petición. */
class EstrategiaBusquedaTest {

    @Test
    @DisplayName("ARQ-06: BusquedaCompleta aplica q, filtros y paginación tal cual")
    void completa() {
        CriterioBusquedaPublica c = new BusquedaCompleta().criterio("rana", "Bufonidae", "15", 2, 10);
        assertThat(c.q()).isEqualTo("rana");
        assertThat(c.familia()).isEqualTo("Bufonidae");
        assertThat(c.departamentoCodigo()).isEqualTo("15");
        assertThat(c.page()).isEqualTo(2);
        assertThat(c.size()).isEqualTo(10);
        assertThat(c.modoDegradado()).isFalse();
        assertThat(c.filtrosIgnorados()).isEmpty();
    }

    @Test
    @DisplayName("ARQ-06 HU-06.4: BusquedaDegradada desactiva familia y departamento, avisa cuáles y conserva q y la paginación")
    void degradada() {
        CriterioBusquedaPublica c = new BusquedaDegradada().criterio("rana", "Bufonidae", "15", 2, 10);
        assertThat(c.familia()).isNull();
        assertThat(c.departamentoCodigo()).isNull();
        assertThat(c.q()).isEqualTo("rana");
        assertThat(c.page()).isEqualTo(2);
        assertThat(c.size()).isEqualTo(10);
        assertThat(c.modoDegradado()).isTrue();
        assertThat(c.filtrosIgnorados()).containsExactly("familia", "departamento");
        assertThat(new BusquedaDegradada().criterio("rana", null, "15", 0, 20).filtrosIgnorados()).containsExactly("departamento");
        assertThat(new BusquedaDegradada().criterio("rana", null, null, 0, 20).filtrosIgnorados()).isEmpty();
    }

    @Test
    @DisplayName("ARQ-06: la clave de caché distingue la búsqueda completa de la degradada (no se mezclan resultados)")
    void clavesDeCacheDistintas() {
        String completa = new BusquedaCompleta().criterio("rana", null, null, 0, 20).claveCache();
        String degradada = new BusquedaDegradada().criterio("rana", null, null, 0, 20).claveCache();
        assertThat(completa).isNotEqualTo(degradada);
    }

    @Test
    @DisplayName("ARQ-06: el elector escoge la estrategia en cada petición según el monitor")
    void electorPorPeticion() {
        MonitorCarga monitor = mock(MonitorCarga.class);
        ElectorEstrategiaBusqueda elector = new ElectorEstrategiaBusqueda(monitor);
        when(monitor.degradado()).thenReturn(false, true, true, false);
        assertThat(elector.actual().nombre()).isEqualTo("completa");
        assertThat(elector.actual().nombre()).isEqualTo("degradada");
        assertThat(elector.actual()).isInstanceOf(BusquedaDegradada.class);
        assertThat(elector.actual()).isInstanceOf(BusquedaCompleta.class);
    }

    @Test
    @DisplayName("ARQ-06 RNF1: con el monitor degradado el validador sigue rechazando entradas inválidas y devuelve el criterio degradado")
    void validadorUsaLaEstrategiaVigente() {
        MonitorCarga monitor = mock(MonitorCarga.class);
        when(monitor.degradado()).thenReturn(true);
        CatalogoDepartamentos catalogo = new CatalogoDepartamentos(() -> java.util.List.of(
                new co.edu.unbosque.biohub.consultapublica.domain.Departamento("15", "Boyacá", "Andina")));
        ValidadorBusqueda validador = new ValidadorBusqueda(catalogo, new ElectorEstrategiaBusqueda(monitor));
        assertThat(validador.validar("rana", "Bufonidae", "Boyacá", null, null).filtrosIgnorados())
                .containsExactly("familia", "departamento");
        when(monitor.degradado()).thenReturn(false);
        assertThat(validador.validar("rana", "Bufonidae", "Boyacá", null, null).departamentoCodigo()).isEqualTo("15");
    }
}
