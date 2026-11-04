package co.edu.unbosque.biohub.cientifico.ubicacion.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.cientifico.ubicacion.service.EstadoConservacion;

/** QA: HB-20/21/24/25, MP-16/20 e inyección en filtros de la búsqueda y el mapa internos. */
class QaInyeccionYLimitesInternosTest extends QaBase {

    private static final String BUSQUEDA = "/api/cientifico/ubicacion/hallazgos";
    private static final String MAPA = "/api/cientifico/ubicacion/mapa";
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final List<String> HOSTILES = List.of("%", "%%", "_", "__", "\\", "\\\\", "'", "''", "\"", "' OR '1'='1",
            "'; DROP TABLE ubicacion_hallazgo; --", "%' OR 1=1 --", "a%", "A_", "+atelopus*", "\"atelopus\"", "-atelopus",
            "@distance", "(atelopus)", "<script>alert(1)</script>", "${jndi:ldap://x/a}", "../../etc/passwd", "Ñandú🐸",
            "\u0000", "atelopus\nX-Injected: 1", "x".repeat(300), "   ", "%25", "%5c", "ate\\lopus");

    @BeforeEach
    void datos() {
        limpiar();
        sembrar("Atelopus ebenoides", LocalDate.of(2026, 3, 14), "investigador1", "15", EstadoConservacion.EN_PELIGRO,
                CENTINELA_1[0], CENTINELA_1[1]);
        sembrar("Atelopus muisca", LocalDate.of(2026, 2, 2), "investigador2", "25", EstadoConservacion.NO_AMENAZADA,
                CENTINELA_2[0], CENTINELA_2[1]);
        sembrar("Rana_x%y", LocalDate.of(2026, 1, 2), "inv_%", "05", EstadoConservacion.SIN_EVALUAR, null, null);
    }

    private MockHttpServletResponse pedir(String url, String auth) throws Exception {
        return mvc.perform(get(java.net.URI.create(url)).header("Authorization", auth)).andReturn().getResponse();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private int total(String url, String auth) throws Exception {
        MockHttpServletResponse r = pedir(url, auth);
        assertThat(r.getStatus()).as(url).isEqualTo(200);
        return JSON.readTree(r.getContentAsString()).get("total").asInt();
    }

    @Test
    void cadaFiltroConTextoHostilResponde200o400NuncaErrorDeServidorYLaTablaSigueIntegra() throws Exception {
        String alto = bearer("juana", "cientifico_alto_rango");
        String inv = bearer("inv", "investigador_campo");
        for (String url : List.of(BUSQUEDA, MAPA)) {
            for (String param : List.of("especie", "investigador", "departamento", "desde", "hasta", "page", "size", "hallazgoId", "bbox")) {
                for (String h : HOSTILES) {
                    for (String auth : List.of(alto, inv)) {
                        MockHttpServletResponse r = pedir(url + "?" + param + "=" + enc(h), auth);
                        assertThat(r.getStatus()).as("%s %s=%s", url, param, h).isIn(200, 400, 403);
                        assertThat(r.getContentAsString()).as("sin trazas %s %s", url, param)
                                .doesNotContain("Exception").doesNotContain("SQL").doesNotContain("at co.edu");
                        assertThat(r.getHeader("Cache-Control")).contains("no-store");
                        if (r.getStatus() == 200 && auth == inv) {
                            assertThat(r.getContentAsString()).doesNotContain("4.6097").doesNotContain("-74.0817");
                        }
                    }
                }
            }
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo", Integer.class)).isEqualTo(3);
    }

    @Test
    void losComodinesDeLikeSeTratanComoTextoLiteral() throws Exception {
        String t = bearer("juana", "cientifico_alto_rango");
        // control positivo: el prefijo normal sí encuentra
        assertThat(total(BUSQUEDA + "?especie=Atelopus", t)).isEqualTo(2);
        // "%%", "_t", "At_" y "%telopus" no deben interpretarse como patrón
        assertThat(total(BUSQUEDA + "?especie=" + enc("%%"), t)).isZero();
        assertThat(total(BUSQUEDA + "?especie=" + enc("_t"), t)).isZero();
        assertThat(total(BUSQUEDA + "?especie=" + enc("At_lopus"), t)).isZero();
        assertThat(total(BUSQUEDA + "?especie=" + enc("%telopus"), t)).isZero();
        assertThat(total(BUSQUEDA + "?especie=" + enc("' OR '1'='1"), t)).isZero();
        // un nombre que contiene % y _ literales se encuentra por su prefijo literal
        assertThat(total(BUSQUEDA + "?especie=" + enc("Rana_x%"), t)).isEqualTo(1);
        assertThat(total(BUSQUEDA + "?especie=" + enc("Rana_x"), t)).isEqualTo(1);
        assertThat(total(BUSQUEDA + "?especie=" + enc("RanaZx"), t)).isZero();
        // investigador con comodines
        assertThat(total(BUSQUEDA + "?investigador=" + enc("%"), t)).as("investigador=% no es comodín").isEqualTo(0);
        assertThat(total(BUSQUEDA + "?investigador=" + enc("inv_%"), t)).isEqualTo(1);
        assertThat(total(BUSQUEDA + "?investigador=" + enc("investigador_"), t)).as("_ no es comodín").isZero();
        // mapa
        assertThat(JSON.readTree(pedir(MAPA + "?especie=" + enc("%%"), t).getContentAsString()).get("total").asInt()).isZero();
        assertThat(JSON.readTree(pedir(MAPA + "?especie=" + enc("A_elopus"), t).getContentAsString()).get("total").asInt()).isZero();
    }

    @Test
    void limiteDePaginacionPageMasUnoPorSizeMenorOIgualA10000() throws Exception {
        String t = bearer("juana", "cientifico_alto_rango");
        record C(int page, int size, int esperado) { }
        for (C c : List.of(new C(0, 1, 200), new C(99, 100, 200), new C(100, 100, 400), new C(9999, 1, 200),
                new C(10000, 1, 400), new C(199, 50, 200), new C(200, 50, 400), new C(0, 100, 200), new C(0, 101, 400),
                new C(0, 0, 400), new C(-1, 20, 400))) {
            assertThat(pedir(BUSQUEDA + "?page=" + c.page() + "&size=" + c.size(), t).getStatus())
                    .as("page=%d size=%d", c.page(), c.size()).isEqualTo(c.esperado());
        }
        // enteros fuera de rango o con formato raro nunca dan 500
        for (String raro : List.of("99999999999", "-99999999999", "2147483648", "1e2", "+5", " 5", "5 ", "0x10",
                "1.0", "٣", "", "NaN")) {
            for (String p : List.of("page", "size")) {
                int st = pedir(BUSQUEDA + "?" + p + "=" + enc(raro), t).getStatus();
                assertThat(st).as("%s=%s", p, raro).isIn(200, 400);
            }
        }
        assertThat(pedir(BUSQUEDA + "?page=21474836&size=100", t).getStatus()).isEqualTo(400);
    }

    /**
     * BUG-J1 (Medio, ubicación): ValidadorConsulta calcula {@code (long) (p + 1) * s}; con page=2147483647 la suma
     * se desborda en int antes del cast, el límite de 10.000 no se aplica y la consulta termina en 500.
     * Esta prueba falla mientras el bug exista.
     */
    @Test
    void bugJ1_pageEnteroMaximoDebeDar400yNoDesbordarElLimite() throws Exception {
        String t = bearer("juana", "cientifico_alto_rango");
        for (String url : List.of(BUSQUEDA)) {
            assertThat(pedir(url + "?page=2147483647&size=100", t).getStatus()).as("page=Integer.MAX_VALUE size=100").isEqualTo(400);
            assertThat(pedir(url + "?page=2147483647", t).getStatus()).as("page=Integer.MAX_VALUE").isEqualTo(400);
        }
    }

    @Test
    void parametrosRepetidosYDesconocidosNoCambianElResultado() throws Exception {
        String t = bearer("juana", "cientifico_alto_rango");
        assertThat(pedir(BUSQUEDA + "?page=0&page=1", t).getStatus()).isIn(200, 400);
        assertThat(pedir(BUSQUEDA + "?especie=Atelopus&especie=Rana", t).getStatus()).isIn(200, 400);
        JsonNode base = JSON.readTree(pedir(BUSQUEDA, t).getContentAsString());
        for (String extra : List.of("rol=administrador", "coordenadas=true", "debug=1", "sort=latitud", "fields=latitud",
                "incluirCoordenadas=true", "lat=4.6", "orderBy=coord_cifrada")) {
            JsonNode otro = JSON.readTree(pedir(BUSQUEDA + "?" + extra, bearer("inv", "investigador_campo")).getContentAsString());
            assertThat(otro.get("total").asInt()).as(extra).isEqualTo(base.get("total").asInt());
            assertThat(otro.toString()).as(extra).doesNotContain("4.6097").doesNotContain("latitud");
        }
    }
}
