package co.edu.unbosque.biohub.consultapublica.eventos;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.testcontainers.junit.jupiter.Testcontainers;

import co.edu.unbosque.biohub.consultapublica.soporte.BaseIntegracion;
import co.edu.unbosque.biohub.consultapublica.soporte.Eventos;

/**
 * Consumidor de hallazgo.* contra RabbitMQ, MySQL y Redis reales. Los eventos se simulan aquí: el
 * publicador real (servicio-cientifico) es de otro equipo y nunca se publica en el broker de las VMs.
 */
@Testcontainers(disabledWithoutDocker = true)
class ConsumidorHallazgosIntegracionTest extends BaseIntegracion {

    private static final Duration ESPERA = Duration.ofSeconds(15);

    private Integer version(UUID id) {
        List<Integer> v = jdbc.sql("SELECT version FROM ficha_publica WHERE hallazgo_id = UNHEX(REPLACE(?, '-', ''))")
                .param(id.toString()).query(Integer.class).list();
        return v.isEmpty() ? null : v.get(0);
    }

    private String estado(UUID id) {
        return jdbc.sql("SELECT estado_conservacion FROM ficha_publica WHERE hallazgo_id = UNHEX(REPLACE(?, '-', ''))")
                .param(id.toString()).query(String.class).single();
    }

    private long fichas() {
        return jdbc.sql("SELECT COUNT(*) FROM ficha_publica").query(Long.class).single();
    }

    private void esperarVersion(UUID id, int version) {
        await().atMost(ESPERA).untilAsserted(() -> assertThat(version(id)).isEqualTo(version));
    }

    /** Publica un evento de control (hallazgo nuevo) y espera a que se aplique: garantiza que los anteriores se procesaron. */
    private void sincronizar(String semilla) {
        UUID control = id("control-" + semilla);
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado",
                Eventos.datos(control, 1, "Control " + semilla, "SIN_EVALUAR")));
        esperarVersion(control, 1);
    }

    @Test
    void altaCreaLaFichaPublicaConRegionDerivadaDelCatalogo() throws Exception {
        UUID id = id("alta");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado",
                Eventos.datos(id, 1, "Atelopus ebenoides", "NO_AMENAZADA")));
        esperarVersion(id, 1);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/publica/fichas/" + id))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.departamento").value("Boyacá"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.region").value("Andina"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.estadoConservacion").value("NO_AMENAZADA"));
    }

    @Test
    void duplicadoSeIgnoraYNoCambiaNada() {
        UUID id = id("duplicado");
        String evento = Eventos.sobre("hallazgo.registrado", Eventos.datos(id, 1, "Atelopus ebenoides", "NO_AMENAZADA"));
        publicar("hallazgo.registrado", evento);
        esperarVersion(id, 1);
        publicar("hallazgo.registrado", evento);
        sincronizar("duplicado");

        assertThat(version(id)).isEqualTo(1);
        assertThat(fichas()).isEqualTo(2); // la ficha y el control
        assertThat(siguienteDeLaDlq(Duration.ofMillis(500))).isNull();
    }

    @Test
    void versionAntiguaOIgualSeIgnora() {
        UUID id = id("version");
        publicar("hallazgo.reclasificado", Eventos.sobre("hallazgo.reclasificado",
                Eventos.datos(id, 3, "Atelopus ebenoides", "EN_PELIGRO")));
        esperarVersion(id, 3);

        publicar("hallazgo.actualizado", Eventos.sobre("hallazgo.actualizado",
                Eventos.datos(id, 2, "Nombre viejo", "NO_AMENAZADA")));
        publicar("hallazgo.actualizado", Eventos.sobre("hallazgo.actualizado",
                Eventos.datos(id, 3, "Nombre igual", "NO_AMENAZADA")));
        sincronizar("version");

        assertThat(version(id)).isEqualTo(3);
        assertThat(estado(id)).isEqualTo("EN_PELIGRO");
        assertThat(jdbc.sql("SELECT nombre_cientifico FROM ficha_publica WHERE hallazgo_id = UNHEX(REPLACE(?, '-', ''))")
                .param(id.toString()).query(String.class).single()).isEqualTo("Atelopus ebenoides");
    }

    @Test
    void reclasificadoAntesQueRegistradoCreaLaFichaYElRegistradoPosteriorSeIgnora() {
        UUID id = id("desorden");
        publicar("hallazgo.reclasificado", Eventos.sobre("hallazgo.reclasificado",
                Eventos.datos(id, 2, "Atelopus ebenoides", "EN_PELIGRO")));
        esperarVersion(id, 2);
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado",
                Eventos.datos(id, 1, "Atelopus ebenoides", "NO_AMENAZADA")));
        sincronizar("desorden");

        assertThat(version(id)).isEqualTo(2);
        assertThat(estado(id)).isEqualTo("EN_PELIGRO");
    }

    @Test
    void reclasificacionActualizaLaFichaEInvalidaLaCachePublica() throws Exception {
        UUID id = id("reclasifica");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado",
                Eventos.datos(id, 1, "Atelopus ebenoides", "NO_AMENAZADA")));
        esperarVersion(id, 1);

        // Calienta las cachés de ficha y de búsqueda con el estado viejo
        var get = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/publica/fichas/" + id);
        var ok = org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.estadoConservacion");
        mvc.perform(get).andExpect(ok.value("NO_AMENAZADA"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/publica/fichas")
                        .param("q", "ebenoides"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.content[0].estadoConservacion").value("NO_AMENAZADA"));
        assertThat(cacheManager.getCache("fichas").get(id.toString())).isNotNull();

        publicar("hallazgo.reclasificado", Eventos.sobre("hallazgo.reclasificado",
                Eventos.datos(id, 2, "Atelopus ebenoides", "EN_PELIGRO")));
        esperarVersion(id, 2);

        // Tras el evento, ambas cachés reflejan el cambio (sin esperar al TTL)
        await().atMost(ESPERA).untilAsserted(() -> {
            mvc.perform(get).andExpect(ok.value("EN_PELIGRO"));
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/publica/fichas")
                            .param("q", "ebenoides"))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                            .jsonPath("$.content[0].estadoConservacion").value("EN_PELIGRO"));
        });
    }

    @Test
    void estadoDesconocidoSeGuardaComoEnPeligro() {
        UUID id = id("desconocido");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado",
                Eventos.datos(id, 1, "Atelopus ebenoides", "CRITICO_RARO")));
        esperarVersion(id, 1);
        assertThat(estado(id)).isEqualTo("EN_PELIGRO");
    }

    @Test
    void departamentoPorNombreSeResuelveAlCodigo() {
        UUID id = id("nombre-depto");
        Map<String, Object> datos = Eventos.datos(id, 1, "Atelopus ebenoides", "SIN_EVALUAR");
        datos.put("departamento", "BOYACÁ");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", datos));
        esperarVersion(id, 1);
        assertThat(jdbc.sql("SELECT departamento_codigo FROM ficha_publica WHERE hallazgo_id = UNHEX(REPLACE(?, '-', ''))")
                .param(id.toString()).query(String.class).single()).isEqualTo("15");
    }

    @Test
    void camposExtraNoProhibidosSeIgnoranYNuncaSeGuardaElUsuario() {
        UUID id = id("extra");
        Map<String, Object> datos = Eventos.datos(id, 1, "Atelopus ebenoides", "SIN_EVALUAR");
        datos.put("campoNuevoDelFuturo", "valor");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", datos));
        esperarVersion(id, 1);
        // La tabla no tiene columna de usuario ni investigador
        assertThat(jdbc.sql("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = 'consulta_publica' AND table_name = 'ficha_publica'
                  AND (column_name LIKE '%usuario%' OR column_name LIKE '%investigador%')""")
                .query(Long.class).single()).isZero();
    }

    private void esperaDlqYSinFicha(String semilla, long fichasAntes) {
        Message enDlq = siguienteDeLaDlq(Duration.ofSeconds(15));
        assertThat(enDlq).as("el evento '%s' debe ir a la DLQ", semilla).isNotNull();
        assertThat(fichas()).isEqualTo(fichasAntes);
    }

    @Test
    void eventoConCoordenadasVaALaDlqSinCrearFicha() {
        Map<String, Object> plano = Eventos.datos(id("c1"), 1, "Atelopus ebenoides", "NO_AMENAZADA");
        plano.put("latitud", 5.5);
        plano.put("longitud", -73.1);
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", plano));
        esperaDlqYSinFicha("coordenadas planas", 0);

        Map<String, Object> anidado = Eventos.datos(id("c2"), 1, "Atelopus ebenoides", "NO_AMENAZADA");
        anidado.put("extra", Map.of("detalle", List.of(Map.of("Coordenadas", "5.5,-73.1"))));
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", anidado));
        esperaDlqYSinFicha("coordenadas anidadas y en mayúsculas", 0);

        // También en reclasificado y actualizado
        Map<String, Object> geom = Eventos.datos(id("c3"), 2, "Atelopus ebenoides", "EN_PELIGRO");
        geom.put("geometry", Map.of("type", "Point"));
        publicar("hallazgo.reclasificado", Eventos.sobre("hallazgo.reclasificado", geom));
        esperaDlqYSinFicha("geometry en reclasificado", 0);
    }

    @Test
    void origenFalsoVaALaDlq() {
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", "servicio-equipos",
                Eventos.datos(id("falso"), 1, "Atelopus ebenoides", "NO_AMENAZADA")));
        esperaDlqYSinFicha("origen falso", 0);
    }

    @Test
    void estadoAusenteVaALaDlq() {
        Map<String, Object> datos = Eventos.datos(id("sin-estado"), 1, "Atelopus ebenoides", "x");
        datos.remove("estadoConservacion");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", datos));
        esperaDlqYSinFicha("estado ausente", 0);
    }

    @Test
    void datosInvalidosVanALaDlq() {
        Map<String, Object> sinNombre = Eventos.datos(id("i1"), 1, "x", "SIN_EVALUAR");
        sinNombre.remove("nombreCientifico");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", sinNombre));
        esperaDlqYSinFicha("sin nombre", 0);

        Map<String, Object> deptoDesconocido = Eventos.datos(id("i2"), 1, "Atelopus ebenoides", "SIN_EVALUAR");
        deptoDesconocido.put("departamento", "Atlantis");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", deptoDesconocido));
        esperaDlqYSinFicha("departamento desconocido", 0);

        Map<String, Object> versionTexto = Eventos.datos(id("i3"), 1, "Atelopus ebenoides", "SIN_EVALUAR");
        versionTexto.put("version", "1");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", versionTexto));
        esperaDlqYSinFicha("version como texto", 0);

        Map<String, Object> largo = Eventos.datos(id("i4"), 1, "x".repeat(151), "SIN_EVALUAR");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", largo));
        esperaDlqYSinFicha("nombre largo", 0);

        Map<String, Object> versionCero = Eventos.datos(id("i5"), 0, "Atelopus ebenoides", "SIN_EVALUAR");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", versionCero));
        esperaDlqYSinFicha("version 0", 0);

        Map<String, Object> fecha = new HashMap<>(Eventos.datos(id("i6"), 1, "Atelopus ebenoides", "SIN_EVALUAR"));
        fecha.put("fechaRecoleccion", "14/03/2026");
        publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", fecha));
        esperaDlqYSinFicha("fecha mal formada", 0);
    }

    @Test
    void routingKeyDistintaDelTipoVaALaDlq() {
        publicar("hallazgo.actualizado", Eventos.sobre("hallazgo.registrado",
                Eventos.datos(id("rk"), 1, "Atelopus ebenoides", "SIN_EVALUAR")));
        esperaDlqYSinFicha("routing key distinta del tipo", 0);
    }
}
