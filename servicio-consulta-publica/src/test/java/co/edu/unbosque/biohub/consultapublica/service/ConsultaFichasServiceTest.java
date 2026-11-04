package co.edu.unbosque.biohub.consultapublica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.consultapublica.service.vista.FichaResumenResponse;
import co.edu.unbosque.biohub.consultapublica.service.vista.PaginaFichasResponse;
import co.edu.unbosque.biohub.consultapublica.infrastructure.BusquedaFichasJdbc;

class ConsultaFichasServiceTest {

    private final BusquedaFichasJdbc repo = mock(BusquedaFichasJdbc.class);
    private final ConsultaFichasService servicio = new ConsultaFichasService(repo);
    private final FichaResumenResponse resumen = new FichaResumenResponse(UUID.randomUUID(), "Atelopus", null,
            "Bufonidae", "EN_PELIGRO", "Boyacá", "15", "Andina");

    @Test
    void calculaTotalPaginasYCopiaLosMetadatos() {
        var criterio = new CriterioBusquedaPublica("atelopus", null, null, 0, 20, false, List.of());
        when(repo.contar(criterio)).thenReturn(41L);
        when(repo.buscar(criterio)).thenReturn(List.of(resumen));
        PaginaFichasResponse p = servicio.buscar(criterio);
        assertThat(p.total()).isEqualTo(41);
        assertThat(p.totalPaginas()).isEqualTo(3);
        assertThat(p.page()).isZero();
        assertThat(p.size()).isEqualTo(20);
        assertThat(p.content()).containsExactly(resumen);
        assertThat(p.modoDegradado()).isFalse();
        assertThat(p.filtrosIgnorados()).isEmpty();
    }

    @Test
    void sinResultadosNoConsultaLaPagina() {
        var criterio = new CriterioBusquedaPublica("zzz", null, null, 0, 20, false, List.of());
        when(repo.contar(criterio)).thenReturn(0L);
        PaginaFichasResponse p = servicio.buscar(criterio);
        assertThat(p.content()).isEmpty();
        assertThat(p.total()).isZero();
        assertThat(p.totalPaginas()).isZero();
        verify(repo, never()).buscar(criterio);
    }

    @Test
    void paginaMasAllaDelFinalDevuelveContenidoVacioConElTotalReal() {
        var criterio = new CriterioBusquedaPublica(null, null, null, 5, 10, false, List.of());
        when(repo.contar(criterio)).thenReturn(25L);
        PaginaFichasResponse p = servicio.buscar(criterio);
        assertThat(p.content()).isEmpty();
        assertThat(p.total()).isEqualTo(25);
        assertThat(p.totalPaginas()).isEqualTo(3);
    }

    @Test
    void modoDegradadoSeReflejaEnLaRespuesta() {
        var criterio = new CriterioBusquedaPublica("rana", null, null, 0, 20, true, List.of("familia", "departamento"));
        when(repo.contar(criterio)).thenReturn(1L);
        when(repo.buscar(criterio)).thenReturn(List.of(resumen));
        PaginaFichasResponse p = servicio.buscar(criterio);
        assertThat(p.modoDegradado()).isTrue();
        assertThat(p.filtrosIgnorados()).containsExactly("familia", "departamento");
    }

    @Test
    void fichaInexistenteLanzaNoEncontrada() {
        UUID id = UUID.randomUUID();
        when(repo.porId(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.obtener(id)).isInstanceOf(FichaNoEncontradaException.class);
    }
}
