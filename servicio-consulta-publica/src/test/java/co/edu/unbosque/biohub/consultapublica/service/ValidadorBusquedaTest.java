package co.edu.unbosque.biohub.consultapublica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.consultapublica.domain.Departamento;

class ValidadorBusquedaTest {

    private final CatalogoDepartamentos catalogo = new CatalogoDepartamentos(() -> List.of(
            new Departamento("15", "Boyacá", "Andina"), new Departamento("11", "Bogotá D.C.", "Andina")));
    private final ValidadorBusqueda validador = validador(false);

    private ValidadorBusqueda validador(boolean forzado) {
        MonitorCarga monitor = new MonitorCarga(UmbralesDegradacion.porDefecto(), forzado);
        return new ValidadorBusqueda(catalogo, new ElectorEstrategiaBusqueda(monitor));
    }

    private static List<ErrorCampo> errores(Runnable accion) {
        try {
            accion.run();
        } catch (ParametroInvalidoException e) {
            return e.errores();
        }
        throw new AssertionError("Se esperaba ParametroInvalidoException");
    }

    @Test
    void valoresPorDefecto() {
        CriterioBusquedaPublica c = validador.validar(null, null, null, null, null);
        assertThat(c.page()).isZero();
        assertThat(c.size()).isEqualTo(20);
        assertThat(c.q()).isNull();
        assertThat(c.modoDegradado()).isFalse();
    }

    @Test
    void recortaEspaciosYElTextoVacioEsAusente() {
        CriterioBusquedaPublica c = validador.validar("  atelopus  ", "  ", " Boyaca ", " 2 ", " 10 ");
        assertThat(c.q()).isEqualTo("atelopus");
        assertThat(c.familia()).isNull();
        assertThat(c.departamentoCodigo()).isEqualTo("15");
        assertThat(c.page()).isEqualTo(2);
        assertThat(c.size()).isEqualTo(10);
    }

    @Test
    void aceptaLosLimitesDeLongitudYBogotaSinSufijo() {
        assertThat(validador.validar("ab", null, null, null, null).q()).isEqualTo("ab");
        assertThat(validador.validar("a".repeat(100), "f".repeat(100), "bogota", "0", "50").departamentoCodigo())
                .isEqualTo("11");
    }

    @Test
    void reuneTodosLosErroresConLosMensajesDelContrato() {
        List<ErrorCampo> e = errores(() -> validador.validar("a", "f".repeat(101), "Atlantis", "-1", "51"));
        assertThat(e).containsExactlyInAnyOrder(
                new ErrorCampo("q", "debe tener entre 2 y 100 caracteres"),
                new ErrorCampo("familia", "máximo 100 caracteres"),
                new ErrorCampo("departamento", "departamento desconocido"),
                new ErrorCampo("page", "debe ser un entero mayor o igual a 0"),
                new ErrorCampo("size", "debe estar entre 1 y 50"));
    }

    @Test
    void qDeMasDe100CaracteresEsInvalida() {
        assertThat(errores(() -> validador.validar("a".repeat(101), null, null, null, null)))
                .containsExactly(new ErrorCampo("q", "debe tener entre 2 y 100 caracteres"));
    }

    @Test
    void enterosMalFormadosOFueraDeRango() {
        for (String malo : new String[] {"abc", "1.5", "99999999999", "1 2"}) {
            assertThat(errores(() -> validador.validar(null, null, null, malo, null))).extracting(ErrorCampo::campo)
                    .containsExactly("page");
            assertThat(errores(() -> validador.validar(null, null, null, null, malo))).extracting(ErrorCampo::campo)
                    .containsExactly("size");
        }
        assertThat(errores(() -> validador.validar(null, null, null, null, "0"))).hasSize(1);
    }

    @Test
    void paginacionProfundaMasAllaDe10000() {
        assertThat(validador.validar(null, null, null, "199", "50").page()).isEqualTo(199); // 200 * 50 = 10.000
        assertThat(errores(() -> validador.validar(null, null, null, "200", "50")))
                .containsExactly(new ErrorCampo("page", "no se puede paginar más allá de 10.000 resultados"));
        assertThat(errores(() -> validador.validar(null, null, null, "10000", "1")))
                .containsExactly(new ErrorCampo("page", "no se puede paginar más allá de 10.000 resultados"));
        assertThat(validador.validar(null, null, null, "9999", "1").page()).isEqualTo(9999);
    }

    @Test
    @DisplayName("ARQ-06 RNF1: forzado manual (CONSULTA_MODO_DEGRADADO) ignora familia y departamento solo si se enviaron")
    void modoDegradadoIgnoraFamiliaYDepartamentoSoloSiSeEnviaron() {
        ValidadorBusqueda degradado = validador(true);
        CriterioBusquedaPublica ambos = degradado.validar("rana", "Bufonidae", "15", "1", "10");
        assertThat(ambos.modoDegradado()).isTrue();
        assertThat(ambos.filtrosIgnorados()).containsExactly("familia", "departamento");
        assertThat(ambos.familia()).isNull();
        assertThat(ambos.departamentoCodigo()).isNull();
        assertThat(ambos.q()).isEqualTo("rana");
        assertThat(ambos.page()).isEqualTo(1);

        assertThat(degradado.validar("rana", "Bufonidae", null, null, null).filtrosIgnorados()).containsExactly("familia");
        assertThat(degradado.validar("rana", null, null, null, null).filtrosIgnorados()).isEmpty();
        // Sigue validando la entrada aunque el filtro se ignore
        assertThatThrownBy(() -> degradado.validar(null, null, "Atlantis", null, null))
                .isInstanceOf(ParametroInvalidoException.class);
    }
}
