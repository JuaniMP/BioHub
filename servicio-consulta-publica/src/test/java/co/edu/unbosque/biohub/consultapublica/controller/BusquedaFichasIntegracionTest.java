package co.edu.unbosque.biohub.consultapublica.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.biohub.consultapublica.domain.EstadoConservacion;
import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.service.ProyectorFichas;
import co.edu.unbosque.biohub.consultapublica.soporte.BaseIntegracion;

/** Búsqueda y ficha públicas contra MySQL 8.4 real (FULLTEXT, prefijo corto, filtros, paginación, errores). */
@Testcontainers(disabledWithoutDocker = true)
class BusquedaFichasIntegracionTest extends BaseIntegracion {

    @Autowired
    private ProyectorFichas proyector;

    private UUID ebenoides;

    private void ficha(UUID id, String cientifico, String comun, String familia, String deptoCodigo,
                       String deptoNombre, String region, EstadoConservacion estado) {
        proyector.aplicar(new FichaPublica(id, 1, cientifico, comun, familia, "Adulto",
                LocalDate.of(2026, 3, 14), estado, deptoCodigo, deptoNombre, region, "Descripción pública"));
    }

    @BeforeEach
    void datos() {
        ebenoides = id("ebenoides");
        ficha(ebenoides, "Atelopus ebenoides", "Rana arlequín de Boyacá", "Bufonidae", "15", "Boyacá", "Andina",
                EstadoConservacion.EN_PELIGRO);
        ficha(id("carrikeri"), "Atelopus carrikeri", "Sapo arlequín de Carrikeri", "Bufonidae", "47", "Magdalena",
                "Caribe", EstadoConservacion.EN_PELIGRO);
        ficha(id("lehmanni"), "Oophaga lehmanni", "Rana venenosa", "Dendrobatidae", "19", "Cauca", "Pacífica",
                EstadoConservacion.EN_PELIGRO);
        ficha(id("marina"), "Rhinella marina", "Sapo común", "Bufonidae", "15", "Boyacá", "Andina",
                EstadoConservacion.NO_AMENAZADA);
        ficha(id("espeletia"), "Espeletia grandiflora", "Frailejón", "Asteraceae", "25", "Cundinamarca", "Andina",
                EstadoConservacion.SIN_EVALUAR);
    }

    @Test
    void listadoSinFiltrosOrdenadoPorNombreCientificoAscendente() throws Exception {
        mvc.perform(get("/api/publica/fichas"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=30"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPaginas").value(1))
                .andExpect(jsonPath("$.modoDegradado").value(false))
                .andExpect(jsonPath("$.filtrosIgnorados", empty()))
                .andExpect(jsonPath("$.content[*].nombreCientifico", contains("Atelopus carrikeri",
                        "Atelopus ebenoides", "Espeletia grandiflora", "Oophaga lehmanni", "Rhinella marina")));
    }

    @Test
    void elElementoDelListadoTraTodosLosCamposDelContrato() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "ebenoides"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(ebenoides.toString()))
                .andExpect(jsonPath("$.content[0].nombreCientifico").value("Atelopus ebenoides"))
                .andExpect(jsonPath("$.content[0].nombreComun").value("Rana arlequín de Boyacá"))
                .andExpect(jsonPath("$.content[0].familia").value("Bufonidae"))
                .andExpect(jsonPath("$.content[0].estadoConservacion").value("EN_PELIGRO"))
                .andExpect(jsonPath("$.content[0].departamento").value("Boyacá"))
                .andExpect(jsonPath("$.content[0].departamentoCodigo").value("15"))
                .andExpect(jsonPath("$.content[0].region").value("Andina"));
    }

    @Test
    void qPorPalabraCompletaYPorPrefijoFulltext() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "atelopus"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/publica/fichas").param("q", "atel"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/publica/fichas").param("q", "atelopus ebeno"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].nombreCientifico").value("Atelopus ebenoides"));
    }

    @Test
    void qBuscaTambienEnElNombreComunSinDistinguirTildesNiMayusculas() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "ARLEQUIN"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/publica/fichas").param("q", "frailejon"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].nombreCientifico").value("Espeletia grandiflora"));
        mvc.perform(get("/api/publica/fichas").param("q", "arlequín"))
                .andExpect(jsonPath("$.total").value(2));
    }

    @Test
    void qCortaDeDosLetrasUsaPrefijoDelNombreCientifico() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "at"))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/publica/fichas").param("q", "RH"))
                .andExpect(jsonPath("$.content[0].nombreCientifico").value("Rhinella marina"));
    }

    @Test
    void loscomodinesDeLikeSeEscapanYNoSeInterpretan() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "a%")).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/publica/fichas").param("q", "_t")).andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void operadoresDeFulltextYInyeccionSeNeutralizan() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "+atelopus -ebenoides"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/publica/fichas").param("q", "' OR '1'='1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/publica/fichas").param("q", "\"atelopus\" (x) ~y @z <w>"))
                .andExpect(status().isOk());
    }

    @Test
    void familiaCoincidenciaExactaSinMayusculasNiTildes() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("familia", "BUFONIDAE"))
                .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/publica/fichas").param("familia", "Bufon"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void departamentoPorCodigoPorNombreYSinTildes() throws Exception {
        for (String valor : new String[] {"15", "Boyacá", "boyaca", "BOYACÁ", " 15 "}) {
            mvc.perform(get("/api/publica/fichas").param("departamento", valor))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(2));
        }
    }

    @Test
    void filtrosCombinados() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "atelopus").param("departamento", "15")
                        .param("familia", "bufonidae"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.content[0].nombreCientifico").value("Atelopus ebenoides"));
        mvc.perform(get("/api/publica/fichas").param("familia", "Bufonidae").param("departamento", "Cauca"))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalPaginas").value(0));
    }

    @Test
    void parametrosVaciosEquivalenAAusentesYLosDesconocidosSeIgnoran() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "  ").param("familia", "").param("departamento", " ")
                        .param("incluirCoordenadas", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(5));
    }

    @Test
    void paginacionBase0ConTotalYTotalPaginas() throws Exception {
        for (int i = 0; i < 25; i++) {
            ficha(id("pag" + i), String.format("Paginus especie%02d", i), null, "Pagidae", "05", "Antioquia",
                    "Andina", EstadoConservacion.SIN_EVALUAR);
        }
        mvc.perform(get("/api/publica/fichas").param("q", "paginus").param("size", "10"))
                .andExpect(jsonPath("$.total").value(25))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.content", hasSize(10)))
                .andExpect(jsonPath("$.content[0].nombreCientifico").value("Paginus especie00"))
                .andExpect(jsonPath("$.content[0].nombreComun").value((Object) null));
        mvc.perform(get("/api/publica/fichas").param("q", "paginus").param("size", "10").param("page", "2"))
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.content", hasSize(5)))
                .andExpect(jsonPath("$.content[0].nombreCientifico").value("Paginus especie20"));
        mvc.perform(get("/api/publica/fichas").param("q", "paginus").param("size", "10").param("page", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void tamanoMaximo50YProfundidadMaxima10000() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("size", "50")).andExpect(status().isOk());
        mvc.perform(get("/api/publica/fichas").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("size"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("debe estar entre 1 y 50"));
        mvc.perform(get("/api/publica/fichas").param("size", "0")).andExpect(status().isBadRequest());
        // (199 + 1) * 50 = 10.000: permitido; (200 + 1) * 50 = 10.050: rechazado
        mvc.perform(get("/api/publica/fichas").param("page", "199").param("size", "50"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/publica/fichas").param("page", "200").param("size", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("page"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("no se puede paginar más allá de 10.000 resultados"));
    }

    @Test
    void parametrosInvalidosDevuelven400ProblemJsonNoStoreConTodosLosErrores() throws Exception {
        mvc.perform(get("/api/publica/fichas").param("q", "a").param("departamento", "Atlantis")
                        .param("page", "-1").param("familia", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.type").value("about:blank"))
                .andExpect(jsonPath("$.title").value("Parámetros inválidos"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("Revisa los parámetros de la consulta."))
                .andExpect(jsonPath("$.instance").value("/api/publica/fichas"))
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.errores", hasSize(4)));
        mvc.perform(get("/api/publica/fichas").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("debe ser un entero mayor o igual a 0"));
        mvc.perform(get("/api/publica/fichas").param("q", "x".repeat(101)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].mensaje").value("debe tener entre 2 y 100 caracteres"));
    }

    @Test
    void fichaDevuelveTodosLosCamposYUbicacionProtegidaSiempre() throws Exception {
        mvc.perform(get("/api/publica/fichas/" + ebenoides))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "public, max-age=60"))
                .andExpect(jsonPath("$.id").value(ebenoides.toString()))
                .andExpect(jsonPath("$.nombreCientifico").value("Atelopus ebenoides"))
                .andExpect(jsonPath("$.nombreComun").value("Rana arlequín de Boyacá"))
                .andExpect(jsonPath("$.familia").value("Bufonidae"))
                .andExpect(jsonPath("$.tipoEspecimen").value("Adulto"))
                .andExpect(jsonPath("$.fechaRecoleccion").value("2026-03-14"))
                .andExpect(jsonPath("$.estadoConservacion").value("EN_PELIGRO"))
                .andExpect(jsonPath("$.departamento").value("Boyacá"))
                .andExpect(jsonPath("$.departamentoCodigo").value("15"))
                .andExpect(jsonPath("$.region").value("Andina"))
                .andExpect(jsonPath("$.descripcion").value("Descripción pública"))
                .andExpect(jsonPath("$.ubicacionProtegida").value(true));
    }

    @Test
    void fichaInexistenteDevuelve404ProblemJsonNoStore() throws Exception {
        String desconocido = UUID.randomUUID().toString();
        mvc.perform(get("/api/publica/fichas/" + desconocido))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"))
                .andExpect(jsonPath("$.detail").value("La ficha solicitada no existe."))
                .andExpect(jsonPath("$.instance").value("/api/publica/fichas/" + desconocido))
                .andExpect(jsonPath("$.errores").doesNotExist());
    }

    @Test
    void idQueNoEsUuidDevuelve400() throws Exception {
        mvc.perform(get("/api/publica/fichas/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.errores[0].campo").value("id"))
                .andExpect(jsonPath("$.errores[0].mensaje").value("identificador inválido"));
    }
}
