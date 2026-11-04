package co.edu.unbosque.biohub.consultapublica.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.consultapublica.domain.EstadoConservacion;
import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.service.ProyectorFichas;
import co.edu.unbosque.biohub.consultapublica.soporte.BaseIntegracion;

/** QA: PB-09..PB-11, PB-20..PB-23, PB-29, SG-02/03/11/12 y límites de paginación (page+1)*size <= 10000. */
@Testcontainers(disabledWithoutDocker = true)
class QaPublicaInyeccionYLimitesTest extends BaseIntegracion {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> PROHIBIDAS = Set.of("lat", "lon", "lng", "latitud", "longitud", "latitude",
            "longitude", "coordinates", "coordenadas", "geometry", "geom", "ubicacionexacta", "usuario", "investigador");
    private static final List<String> HOSTILES = List.of("%", "%%", "_", "__", "\\", "\\\\", "'", "''", "\"", "' OR '1'='1",
            "'; DROP TABLE ficha_publica; --", "%' OR 1=1 --", "a%", "A_", "+atelopus*", "\"atelopus\"", "-atelopus", "+",
            "-", "*", "~", "<", ">", "()", "(atelopus)", "@distance", "atelopus -rana +boana", "@@version",
            "<script>alert(1)</script>", "<img src=x onerror=alert(1)>", "${jndi:ldap://x/a}", "../../etc/passwd", "Ñandú🐸",
            "\u0000", "atelopus\r\nX-Injected: 1", "x".repeat(300), "   ", "%25", "%5c", "ate\\lopus", "AGAINST('x')",
            "atelopus) AGAINST ('x' IN BOOLEAN MODE) OR 1=1 --");
    private static final String BASE = "/api/publica/fichas";

    @Autowired
    private ProyectorFichas proyector;

    @BeforeEach
    void datos() {
        sembrar("Atelopus ebenoides", "Bufonidae", "15", "Boyacá", "Andina");
        sembrar("Atelopus muisca", "Bufonidae", "25", "Cundinamarca", "Andina");
        sembrar("Boana_x%y", "Hylidae", "05", "Antioquia", "Andina");
        sembrar("Rana ñandú", "Ranidae", "15", "Boyacá", "Andina");
    }

    private void sembrar(String nombre, String familia, String cod, String depto, String region) {
        proyector.aplicar(new FichaPublica(UUID.nameUUIDFromBytes(nombre.getBytes()), 1, nombre, "común " + nombre, familia,
                "Adulto", LocalDate.of(2026, 3, 14), EstadoConservacion.EN_PELIGRO, cod, depto, region, "descripción"));
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Codifica el segmento de ruta conservando las secuencias %XX que el caso ya trae a propósito (p. ej. %00). */
    private static String encRuta(String s) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int c = bytes[i] & 0xFF;
            boolean seguro = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '-' || c == '.' || c == '_' || c == '~' || c == '%';
            if (seguro) {
                sb.append((char) c);
            } else {
                sb.append('%').append(String.format("%02X", c));
            }
        }
        return sb.toString();
    }

    private MockHttpServletResponse pedir(String url) throws Exception {
        return mvc.perform(get(java.net.URI.create(url))).andReturn().getResponse();
    }

    private int total(String url) throws Exception {
        MockHttpServletResponse r = pedir(url);
        assertThat(r.getStatus()).as(url).isEqualTo(200);
        return JSON.readTree(r.getContentAsString()).get("total").asInt();
    }

    private static void claves(JsonNode n, List<String> out) {
        if (n.isObject()) {
            n.fields().forEachRemaining(e -> {
                out.add(e.getKey().toLowerCase(Locale.ROOT));
                claves(e.getValue(), out);
            });
        } else if (n.isArray()) {
            n.forEach(x -> claves(x, out));
        }
    }

    @Test
    void textoHostilEnCualquierParametroDa200o400NuncaErrorDeServidorNiTrazasNiReflejo() throws Exception {
        for (String param : List.of("q", "familia", "departamento", "page", "size")) {
            for (String h : HOSTILES) {
                MockHttpServletResponse r = pedir(BASE + "?" + param + "=" + enc(h));
                String cuerpo = r.getContentAsString();
                assertThat(r.getStatus()).as("%s=%s -> %s", param, h, cuerpo).isIn(200, 400);
                assertThat(cuerpo).as("sin trazas %s=%s", param, h).doesNotContain("Exception").doesNotContain("SQL")
                        .doesNotContain("at co.edu").doesNotContain("com.mysql");
                assertThat(cuerpo).as("sin reflejo de HTML %s=%s", param, h).doesNotContain("<script").doesNotContain("<img");
                assertThat(r.getContentType()).contains("json");
                assertThat(r.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
                List<String> k = new ArrayList<>();
                claves(JSON.readTree(cuerpo), k);
                assertThat(k).doesNotContainAnyElementsOf(PROHIBIDAS);
            }
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM ficha_publica").query(Integer.class).single()).isEqualTo(4);
    }

    @Test
    void comodinesYOperadoresNoSeInterpretan() throws Exception {
        assertThat(total(BASE + "?q=Atelopus")).as("control positivo").isEqualTo(2);
        for (String h : List.of("%%", "_t", "At_", "%telopus", "A%", "_%", "\\\\", "' OR '1'='1", "atelopus' OR '1'='1",
                "*", "(atelopus", "atelopus)")) {
            int t;
            MockHttpServletResponse r = pedir(BASE + "?q=" + enc(h));
            if (r.getStatus() == 400) {
                continue; // rechazado por validación: aceptable
            }
            t = JSON.readTree(r.getContentAsString()).get("total").asInt();
            // ninguna búsqueda hostil puede devolver más que las coincidencias legítimas del término
            assertThat(t).as("q=%s no debe comportarse como comodín (devolvió todo)", h).isLessThan(4);
        }
        assertThat(total(BASE + "?q=" + enc("%%"))).isZero();
        assertThat(total(BASE + "?q=" + enc("_t"))).isZero();
        assertThat(total(BASE + "?familia=" + enc("%"))).as("familia=% no es comodín").isZero();
        assertThat(total(BASE + "?familia=" + enc("Buf%"))).isZero();
        assertThat(total(BASE + "?familia=" + enc("B_fonidae"))).isZero();
        assertThat(total(BASE + "?familia=Bufonidae")).isEqualTo(2);
        // operadores de FULLTEXT: "+" y "-" no excluyen ni exigen nada, "*" no es comodín total
        assertThat(total(BASE + "?q=" + enc("-atelopus"))).isEqualTo(total(BASE + "?q=atelopus"));
        assertThat(total(BASE + "?q=" + enc("+atelopus"))).isEqualTo(total(BASE + "?q=atelopus"));
        // D26/J7: los operadores se descartan y cada palabra restante es obligatoria (+pal*), es decir un AND. Por eso
        // "atelopus -muisca" equivale a "atelopus muisca" (1 resultado: Atelopus muisca). El '-' ni excluye
        // (excluir daría Atelopus ebenoides) ni convierte la búsqueda en un OR (daría 2). La expectativa original (2)
        // era un error del test: suponía un OR que el diseño no tiene.
        assertThat(total(BASE + "?q=" + enc("atelopus -muisca"))).as("el operador - no excluye: se ignora")
                .isEqualTo(total(BASE + "?q=" + enc("atelopus muisca"))).isEqualTo(1);
        JsonNode soloMuisca = JSON.readTree(pedir(BASE + "?q=" + enc("atelopus -muisca")).getContentAsString());
        assertThat(soloMuisca.get("content").get(0).get("nombreCientifico").asText()).isEqualTo("Atelopus muisca");
    }

    @Test
    void limitesDePaginacion() throws Exception {
        record C(int page, int size, int esperado) { }
        for (C c : List.of(new C(0, 1, 200), new C(0, 50, 200), new C(0, 51, 400), new C(0, 0, 400), new C(-1, 20, 400),
                new C(9999, 1, 200), new C(10000, 1, 400), new C(199, 50, 200), new C(200, 50, 400), new C(99, 100, 400))) {
            assertThat(pedir(BASE + "?page=" + c.page() + "&size=" + c.size()).getStatus())
                    .as("page=%d size=%d", c.page(), c.size()).isEqualTo(c.esperado());
        }
        for (String p : List.of("2147483647", "2147483648", "99999999999999999999", "-2147483649", "1e2", "+5", " 5", "0x10",
                "1.0", "٣", "NaN", "null")) {
            for (String nombre : List.of("page", "size")) {
                int st = pedir(BASE + "?" + nombre + "=" + enc(p)).getStatus();
                assertThat(st).as("%s=%s", nombre, p).isIn(200, 400);
            }
        }
        assertThat(pedir(BASE + "?page=2147483647&size=50").getStatus()).as("overflow de (page+1)*size").isEqualTo(400);
        assertThat(pedir(BASE + "?page=2147483647").getStatus()).isEqualTo(400);
        // última página legal vacía pero válida
        MockHttpServletResponse r = pedir(BASE + "?page=9999&size=1");
        assertThat(JSON.readTree(r.getContentAsString()).get("content")).isEmpty();
    }

    @Test
    void soloLecturaPublicaYSinTokenLosDemasMetodosNoSeAceptan() throws Exception {
        UUID id = UUID.nameUUIDFromBytes("Atelopus ebenoides".getBytes());
        for (var req : List.of(post(BASE), put(BASE + "/" + id), delete(BASE + "/" + id), patch(BASE + "/" + id),
                post(BASE + "/" + id), delete(BASE))) {
            MockHttpServletResponse r = mvc.perform(req).andReturn().getResponse();
            assertThat(r.getStatus()).isIn(401, 403, 405);
        }
        assertThat(pedir(BASE + "/" + id).getStatus()).isEqualTo(200);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM ficha_publica").query(Integer.class).single()).isEqualTo(4);
    }

    @Test
    void idsRaros() throws Exception {
        for (String id : List.of("1", "abc", "%00", "..%2f..%2fetc%2fpasswd", "' OR '1'='1", "00000000-0000-0000-0000-000000000000",
                "ZZZZZZZZ-ZZZZ-ZZZZ-ZZZZ-ZZZZZZZZZZZZ", UUID.randomUUID().toString().toUpperCase(Locale.ROOT) + "x",
                "%3Cscript%3E")) {
            // el test debe codificar la ruta: URI.create rechaza comillas y espacios (era un error del test)
            MockHttpServletResponse r = pedir(BASE + "/" + encRuta(id));
            assertThat(r.getStatus()).as(id).isIn(400, 404);
            assertThat(r.getContentAsString()).doesNotContain("Exception").doesNotContain("<script");
        }
    }

    @Test
    void cabecerasDeSeguridadYCacheEnTodasLasRespuestas() throws Exception {
        UUID id = UUID.nameUUIDFromBytes("Atelopus ebenoides".getBytes());
        for (String url : List.of(BASE, BASE + "/" + id, BASE + "/" + UUID.randomUUID(), BASE + "/xx", BASE + "?size=0")) {
            MockHttpServletResponse r = pedir(url);
            assertThat(r.getHeader("X-Content-Type-Options")).as(url).isEqualTo("nosniff");
            assertThat(r.getHeader("Server")).as("sin cabecera Server que revele versión").isNull();
            assertThat(r.getHeader("X-Powered-By")).isNull();
            if (r.getStatus() >= 400) {
                assertThat(r.getHeader("Cache-Control")).as("errores no cacheables " + url).contains("no-store");
            }
        }
    }
}
