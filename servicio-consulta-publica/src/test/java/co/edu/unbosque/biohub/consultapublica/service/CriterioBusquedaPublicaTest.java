package co.edu.unbosque.biohub.consultapublica.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CriterioBusquedaPublicaTest {

    private static CriterioBusquedaPublica con(String q, String familia, String depto) {
        return new CriterioBusquedaPublica(q, familia, depto, 0, 20, false, List.of());
    }

    @Test
    void expresionFulltextUsaMasPalabraAsterisco() {
        assertThat(con("atelopus ebeno", null, null).expresionFulltext()).contains("+atelopus* +ebeno*");
    }

    @Test
    void quitaOperadoresYSignosDeFulltext() {
        assertThat(con("+atelopus -ebenoides (x) ~\"y\" @ <z>*", null, null).expresionFulltext())
                .contains("+atelopus* +ebenoides*");
        assertThat(con("' OR '1'='1", null, null).expresionFulltext()).isEmpty();
    }

    @Test
    void descartaPalabrasDeMenosDeTresLetras() {
        assertThat(con("rana de boyaca", null, null).expresionFulltext()).contains("+rana* +boyaca*");
    }

    @Test
    void sinPalabrasDeTresLetrasNoHayFulltextYSeUsaPrefijoLikeEscapado() {
        CriterioBusquedaPublica c = con("at", null, null);
        assertThat(c.expresionFulltext()).isEmpty();
        assertThat(c.patronPrefijoLike()).isEqualTo("at%");
        assertThat(con("a%_\\", null, null).patronPrefijoLike()).isEqualTo("a\\%\\_\\\\%");
    }

    @Test
    void conservaPalabrasConTildesYNumeros() {
        assertThat(con("Cañón 2026", null, null).expresionFulltext()).contains("+Cañón* +2026*");
    }

    @Test
    void sinQNoHayExpresion() {
        assertThat(con(null, "Bufonidae", null).expresionFulltext()).isEmpty();
        assertThat(con(null, null, null).palabrasFulltext()).isEmpty();
    }

    @Test
    void offsetEsPageTimesSize() {
        assertThat(new CriterioBusquedaPublica(null, null, null, 3, 20, false, List.of()).offset()).isEqualTo(60);
        assertThat(new CriterioBusquedaPublica(null, null, null, 199, 50, false, List.of()).offset()).isEqualTo(9950);
    }

    @Test
    void claveDeCacheIgnoraMayusculasTildesYEspaciosExternosPeroNoLosFiltros() {
        assertThat(con("Arlequín", "Bufonidae", "15").claveCache()).isEqualTo(con("arlequin", "BUFONIDAE", "15").claveCache());
        assertThat(con("arlequin", null, "15").claveCache()).isNotEqualTo(con("arlequin", null, "25").claveCache());
        assertThat(con("a", null, null).claveCache()).isEqualTo("q=a|f=|d=|p=0|s=20|deg=false");
    }

    @Test
    void claveDeCacheNoPuedeSerForjadaConSeparadores() {
        assertThat(con("x|f=bufonidae", null, null).claveCache()).isNotEqualTo(con("x", "bufonidae", null).claveCache());
        assertThat(con("x|d=15", null, null).claveCache()).isNotEqualTo(con("x", null, "15").claveCache());
    }

    @Test
    void claveDeCacheDistingueElModoDegradadoYSusFiltrosIgnorados() {
        var normal = new CriterioBusquedaPublica("x", null, null, 0, 20, false, List.of());
        var degradado = new CriterioBusquedaPublica("x", null, null, 0, 20, true, List.of("familia"));
        var degradado2 = new CriterioBusquedaPublica("x", null, null, 0, 20, true, List.of());
        assertThat(normal.claveCache()).isNotEqualTo(degradado.claveCache());
        assertThat(degradado.claveCache()).isNotEqualTo(degradado2.claveCache());
    }
}
