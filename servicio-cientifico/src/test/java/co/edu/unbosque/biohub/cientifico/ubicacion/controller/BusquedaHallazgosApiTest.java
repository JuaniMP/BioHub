package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import co.edu.unbosque.biohub.cientifico.ubicacion.BaseUbicacionMySqlTest;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.EstadoConservacion;

class BusquedaHallazgosApiTest extends BaseUbicacionMySqlTest {

    private static final String URL = "/api/cientifico/ubicacion/hallazgos";
    private UUID a;
    private UUID b;
    private UUID c;
    private UUID d;

    @BeforeEach
    void datos() {
        a = sembrar("Atelopus ebenoides", LocalDate.of(2026, 3, 14), "investigador1", "Boyacá", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.1234567);
        b = sembrar("Atelopus muisca", LocalDate.of(2026, 2, 2), "investigador2", "Cundinamarca", EstadoConservacion.EN_PELIGRO, 4.711, -74.0721);
        c = sembrar("Rhinella marina", LocalDate.of(2025, 12, 1), "investigador1", "Boyacá", EstadoConservacion.NO_AMENAZADA, null, null);
        d = sembrar("Dendrobates truncatus", LocalDate.of(2026, 1, 20), "investigador3", "Chocó", EstadoConservacion.NO_AMENAZADA, 7.5, -76.6);
    }

    private ResultActions buscar(String auth, String query) throws Exception {
        return mvc.perform(get(java.net.URI.create(URL + query.replace(" ", "%20"))).header("Authorization", auth));
    }

    // --- autenticación y roles ---

    @Test
    void sinTokenDa401SinCuerpo() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", containsString("Bearer")))
                .andExpect(content().string(""));
    }

    @Test
    void tokenVencidoDa401() throws Exception {
        buscar(bearerVencido(), "").andExpect(status().isUnauthorized()).andExpect(content().string(""));
    }

    @Test
    void altoRangoVeLatitudYLongitud() throws Exception {
        buscar(bearer("juana", "cientifico_alto_rango"), "?especie=Atelopus%20ebenoides")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(a.toString()))
                .andExpect(jsonPath("$.content[0].estadoCoordenadas").value("VISIBLES"))
                .andExpect(jsonPath("$.content[0].ubicacionProtegida").value(false))
                .andExpect(jsonPath("$.content[0].latitud").value(5.1234567))
                .andExpect(jsonPath("$.content[0].longitud").value(-73.1234567))
                .andExpect(jsonPath("$.content[0].departamento").value("Boyacá"))
                .andExpect(jsonPath("$.content[0].departamentoCodigo").value("15"))
                .andExpect(jsonPath("$.content[0].region").value("Andina"))
                .andExpect(jsonPath("$.content[0].estadoConservacion").value("EN_PELIGRO"))
                .andExpect(jsonPath("$.omitidos").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"investigador_campo", "auditor_cientifico", "analista_ambiental", "administrador"})
    void losDemasRolesNoVenCoordenadasNiDeEspeciesNoAmenazadas(String rol) throws Exception {
        String cuerpo = buscar(bearer("u", rol), "").andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.omitidos").value(0))
                .andExpect(jsonPath("$.content[?(@.id=='" + a + "')].estadoCoordenadas").value("PROTEGIDAS"))
                .andExpect(jsonPath("$.content[?(@.id=='" + a + "')].ubicacionProtegida").value(true))
                .andExpect(jsonPath("$.content[?(@.id=='" + d + "')].estadoCoordenadas").value("PROTEGIDAS")) // NO_AMENAZADA también
                .andExpect(jsonPath("$.content[?(@.id=='" + c + "')].estadoCoordenadas").value("SIN_COORDENADAS"))
                .andExpect(jsonPath("$.content[?(@.id=='" + c + "')].ubicacionProtegida").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("latitud").doesNotContain("longitud")
                .doesNotContain("5.1234567").doesNotContain("73.1234567").doesNotContain("4.711").doesNotContain("7.5");
    }

    @Test
    void siElJwtTieneVariosRolesGanaElDeAltoRango() throws Exception {
        buscar(bearer("u", "investigador_campo", "cientifico_alto_rango"), "?especie=Rhinella")
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].estadoCoordenadas").value("SIN_COORDENADAS"));
        buscar(bearer("u", "investigador_campo", "cientifico_alto_rango"), "?especie=Dendrobates")
                .andExpect(jsonPath("$.content[0].latitud").value(7.5));
    }

    @Test
    void autenticadoSinRolesInternosDa403ProblemJson() throws Exception {
        buscar(bearer("intruso", "offline_access"), "")
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.codigo").value("ROL_INSUFICIENTE"))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.title").value("Acceso denegado"))
                .andExpect(jsonPath("$.detail").value("No tienes permiso para ver esta información."))
                .andExpect(jsonPath("$.instance").value(URL))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.content").doesNotExist());
    }

    // --- filtros combinables, orden y paginación ---

    @Test
    void ordenaPorFechaDescendente() throws Exception {
        buscar(bearer("u", "auditor_cientifico"), "")
                .andExpect(jsonPath("$.content[0].id").value(a.toString()))
                .andExpect(jsonPath("$.content[1].id").value(b.toString()))
                .andExpect(jsonPath("$.content[2].id").value(d.toString()))
                .andExpect(jsonPath("$.content[3].id").value(c.toString()));
    }

    @Test
    void filtraPorEspecieSinDistinguirMayusculasNiTildesYPorPrefijo() throws Exception {
        String t = bearer("u", "auditor_cientifico");
        buscar(t, "?especie=ATELOPUS").andExpect(jsonPath("$.total").value(2));
        buscar(t, "?especie=atélopus").andExpect(jsonPath("$.total").value(2));
        buscar(t, "?especie=Atelopus%20m").andExpect(jsonPath("$.total").value(1));
        buscar(t, "?especie=muisca").andExpect(jsonPath("$.total").value(0)); // empieza por, no contiene
        buscar(t, "?especie=At%25").andExpect(jsonPath("$.total").value(0));  // % no es comodín
        buscar(t, "?especie=%20%20").andExpect(jsonPath("$.total").value(4)); // vacío = ausente
    }

    @Test
    void filtraPorRangoDeFechasInclusivo() throws Exception {
        String t = bearer("u", "auditor_cientifico");
        buscar(t, "?desde=2026-02-02&hasta=2026-03-14").andExpect(jsonPath("$.total").value(2));
        buscar(t, "?desde=2026-03-15").andExpect(jsonPath("$.total").value(0));
        buscar(t, "?hasta=2025-12-01").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void filtraPorInvestigadorYDepartamentoPorCodigoONombre() throws Exception {
        String t = bearer("u", "auditor_cientifico");
        buscar(t, "?investigador=investigador1").andExpect(jsonPath("$.total").value(2));
        buscar(t, "?departamento=15").andExpect(jsonPath("$.total").value(2));
        buscar(t, "?departamento=boyaca").andExpect(jsonPath("$.total").value(2));
        buscar(t, "?departamento=Boyacá").andExpect(jsonPath("$.total").value(2));
        buscar(t, "?departamento=27").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void losFiltrosSeCombinan() throws Exception {
        String t = bearer("juana", "cientifico_alto_rango");
        buscar(t, "?especie=Atelopus&investigador=investigador1&departamento=15&desde=2026-01-01&hasta=2026-12-31")
                .andExpect(jsonPath("$.total").value(1)).andExpect(jsonPath("$.content[0].id").value(a.toString()))
                .andExpect(jsonPath("$.content[0].latitud").value(5.1234567));
        buscar(t, "?especie=Atelopus&investigador=investigador1&departamento=25").andExpect(jsonPath("$.total").value(0));
        buscar(t, "?investigador=investigador1&desde=2026-01-01").andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void sinResultadosDevuelve200Vacio() throws Exception {
        buscar(bearer("u", "administrador"), "?especie=Inexistente")
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.total").value(0)).andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void paginacion() throws Exception {
        String t = bearer("u", "auditor_cientifico");
        buscar(t, "?page=0&size=3").andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(3))
                .andExpect(jsonPath("$.total").value(4)).andExpect(jsonPath("$.totalPaginas").value(2));
        buscar(t, "?page=1&size=3").andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(c.toString()));
        buscar(t, "?page=5&size=3").andExpect(jsonPath("$.content.length()").value(0)).andExpect(jsonPath("$.total").value(4));
        buscar(t, "").andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        buscar(t, "?size=100").andExpect(status().isOk());
    }

    @Test
    void parametrosDesconocidosSeIgnoran() throws Exception {
        buscar(bearer("u", "investigador_campo"), "?incluirCoordenadas=true&foo=bar")
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].latitud").doesNotExist());
    }

    // --- validaciones ---

    private void invalido(String query, String campo, String mensaje) throws Exception {
        buscar(bearer("u", "auditor_cientifico"), query)
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value(campo))
                .andExpect(jsonPath("$.errores[0].mensaje").value(mensaje));
    }

    @Test
    void validaciones() throws Exception {
        invalido("?desde=2026-06-01&hasta=2026-01-01", "hasta", "debe ser igual o posterior a 'desde'");
        invalido("?desde=01-06-2026", "desde", "fecha inválida, formato AAAA-MM-DD");
        invalido("?hasta=2026-02-30", "hasta", "fecha inválida, formato AAAA-MM-DD");
        invalido("?size=101", "size", "debe estar entre 1 y 100");
        invalido("?size=0", "size", "debe estar entre 1 y 100");
        invalido("?page=-1", "page", "debe ser un entero mayor o igual a 0");
        invalido("?page=abc", "page", "debe ser un entero mayor o igual a 0");
        invalido("?especie=" + "x".repeat(101), "especie", "debe tener entre 2 y 100 caracteres");
        invalido("?especie=a", "especie", "debe tener entre 2 y 100 caracteres");
        invalido("?departamento=Narnia", "departamento", "departamento desconocido");
        invalido("?page=100&size=100", "page", "no se puede paginar más allá de 10.000 resultados");
        invalido("?investigador=" + "i".repeat(101), "investigador", "debe tener entre 1 y 100 caracteres");
    }

    @Test
    void seReportanVariosErroresAlMismoTiempo() throws Exception {
        buscar(bearer("u", "auditor_cientifico"), "?size=0&departamento=Narnia")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errores.length()").value(2));
    }

    @Test
    void bboxReservado() throws Exception {
        buscar(bearer("u", "analista_ambiental"), "?bbox=1,2,3,4").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ROL_INSUFICIENTE"));
        buscar(bearer("juana", "cientifico_alto_rango"), "?bbox=1,2,3,4").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("NO_SOPORTADO"));
    }

    // --- coordenadas no descifrables ---

    @Test
    void llaveRetiradaOAlteradoSeMarcaNoDisponibleSoloParaAltoRango() throws Exception {
        jdbc.update("UPDATE ubicacion_hallazgo SET clave_version = 'v9' WHERE nombre_cientifico = 'Atelopus muisca'");
        jdbc.update("UPDATE ubicacion_hallazgo SET coord_cifrada = CONCAT(UNHEX('FF'), SUBSTRING(coord_cifrada, 2))"
                + " WHERE nombre_cientifico = 'Dendrobates truncatus'");
        buscar(bearer("juana", "cientifico_alto_rango"), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.omitidos").value(2))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.content[?(@.id=='" + b + "')].estadoCoordenadas").value("NO_DISPONIBLES"))
                .andExpect(jsonPath("$.content[?(@.id=='" + b + "')].latitud").isEmpty())
                .andExpect(jsonPath("$.content[?(@.id=='" + d + "')].estadoCoordenadas").value("NO_DISPONIBLES"))
                .andExpect(jsonPath("$.content[?(@.id=='" + a + "')].estadoCoordenadas").value("VISIBLES"));
        buscar(bearer("u", "auditor_cientifico"), "")
                .andExpect(jsonPath("$.omitidos").value(0))
                .andExpect(jsonPath("$.content[?(@.id=='" + b + "')].estadoCoordenadas").value("PROTEGIDAS"));
    }
}
