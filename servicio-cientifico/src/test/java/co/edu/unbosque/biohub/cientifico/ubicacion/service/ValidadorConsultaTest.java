package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Constructor;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CriterioBusquedaInterna;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Departamento;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.DepartamentoRepository;

class ValidadorConsultaTest {

    private final CatalogoDepartamentos catalogo = new CatalogoDepartamentos(mock(DepartamentoRepository.class)) {
        @Override
        public Optional<Departamento> resolver(String texto) {
            return Optional.empty();
        }
    };

    private ValidadorConsulta v() {
        return new ValidadorConsulta(catalogo);
    }

    @Test
    void recortaYVacioEsAusente() {
        assertThat(ValidadorConsulta.recortar("  ")).isNull();
        assertThat(ValidadorConsulta.recortar("  ab ")).isEqualTo("ab");
        assertThat(v().texto("especie", "   ", 2, 100, "m")).isNull();
        assertThat(v().fecha("desde", "")).isNull();
    }

    @Test
    void fechasEstrictas() {
        assertThat(v().fecha("desde", "2026-01-31")).isEqualTo(LocalDate.of(2026, 1, 31));
        for (String mala : new String[] {"2026-02-30", "31/01/2026", "2026-1-1", "abc", "+12026-01-01"}) {
            ValidadorConsulta val = v();
            val.fecha("desde", mala);
            assertThatThrownBy(val::lanzarSiHayErrores).isInstanceOfSatisfying(ParametroInvalidoException.class,
                    e -> assertThat(e.errores()).containsExactly(new ErrorCampo("desde", "fecha inválida, formato AAAA-MM-DD")));
        }
    }

    @Test
    void desdePosteriorAHastaEsError() {
        ValidadorConsulta val = v();
        val.rangoFechas(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 1, 1));
        assertThatThrownBy(val::lanzarSiHayErrores).isInstanceOfSatisfying(ParametroInvalidoException.class,
                e -> assertThat(e.errores()).containsExactly(new ErrorCampo("hasta", "debe ser igual o posterior a 'desde'")));
    }

    @Test
    void paginacionPorDefectoYLimites() {
        assertThat(v().paginacion(null, null, 100)).containsExactly(0, 20);
        assertThat(v().paginacion("2", "50", 100)).containsExactly(2, 50);
        // (page + 1) * size <= 10.000
        assertThat(v().paginacion("99", "100", 100)).containsExactly(99, 100);
        for (String[] malo : new String[][] {{"-1", "10"}, {"x", "10"}, {"0", "0"}, {"0", "101"}, {"100", "100"}}) {
            ValidadorConsulta val = v();
            val.paginacion(malo[0], malo[1], 100);
            assertThatThrownBy(val::lanzarSiHayErrores).isInstanceOf(ParametroInvalidoException.class);
        }
    }

    @Test
    @org.junit.jupiter.api.DisplayName("BUG-J1: page=Integer.MAX_VALUE no desborda el límite de 10.000 (RF12/RF13, HU-13)")
    void bugJ1_pageEnteroMaximoNoDesbordaElLimite() {
        for (String[] extremo : new String[][] {{"2147483647", "100"}, {"2147483647", "1"}, {"2147483647", null},
                {"2147483646", "2"}, {"21474836", "100"}}) {
            ValidadorConsulta val = v();
            val.paginacion(extremo[0], extremo[1], 100);
            assertThatThrownBy(val::lanzarSiHayErrores).as("page=%s size=%s", extremo[0], extremo[1])
                    .isInstanceOfSatisfying(ParametroInvalidoException.class,
                            e -> assertThat(e.errores()).extracting(ErrorCampo::campo).contains("page"));
        }
        // el límite exacto sigue siendo válido
        assertThat(v().paginacion("9999", "1", 100)).containsExactly(9999, 1);
    }

    @Test
    void textoFueraDeRango() {
        ValidadorConsulta val = v();
        assertThat(val.texto("especie", "a", 2, 100, "debe tener entre 2 y 100 caracteres")).isNull();
        assertThat(val.texto("especie", "x".repeat(101), 2, 100, "m")).isNull();
        assertThatThrownBy(val::lanzarSiHayErrores).isInstanceOfSatisfying(ParametroInvalidoException.class,
                e -> assertThat(e.errores()).hasSize(2));
    }

    @Test
    void uuidInvalidoYDepartamentoDesconocido() {
        ValidadorConsulta val = v();
        assertThat(val.uuid("hallazgoId", "no-es-uuid")).isNull();
        assertThat(val.departamento("Narnia")).isNull();
        assertThatThrownBy(val::lanzarSiHayErrores).isInstanceOfSatisfying(ParametroInvalidoException.class,
                e -> assertThat(e.errores()).containsExactly(
                        new ErrorCampo("hallazgoId", "identificador inválido"),
                        new ErrorCampo("departamento", "departamento desconocido")));
    }

    @Test
    void criterioEscapaLosComodinesDeLike() {
        assertThat(CriterioBusquedaInterna.escaparLike("a%b_c!d")).isEqualTo("a!%b!_c!!d");
        CriterioBusquedaInterna c = new CriterioBusquedaInterna("x", null, null, null, null, null, false);
        assertThat(c.clausulaWhere()).contains(":especie").doesNotContain("'x'");
    }

    @Test
    void normalizaNombresDeDepartamento() {
        assertThat(CatalogoDepartamentos.normalizar("  BoYacá ")).isEqualTo("boyaca");
        assertThat(CatalogoDepartamentos.normalizar("San   Andrés y Providencia")).isEqualTo("san andres y providencia");
    }
}
