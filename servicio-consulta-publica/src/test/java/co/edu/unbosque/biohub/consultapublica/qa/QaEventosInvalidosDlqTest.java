package co.edu.unbosque.biohub.consultapublica.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.testcontainers.junit.jupiter.Testcontainers;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.biohub.consultapublica.soporte.BaseIntegracion;
import co.edu.unbosque.biohub.consultapublica.soporte.Eventos;

/**
 * QA: CP-04..CP-13, CP-21, CP-25. Una tanda de eventos inválidos (tipos, límites, sobres rotos) debe terminar
 * íntegra en la DLQ sin crear fichas y sin bloquear la cola (un evento válido posterior se consume). Los eventos con
 * coordenadas (SEC-J-02/J-03) NO van a la DLQ: se descartan con ack y no dejan rastro de las coordenadas centinela
 * en BD, Redis, logs ni DLQ.
 */
@Testcontainers(disabledWithoutDocker = true)
class QaEventosInvalidosDlqTest extends BaseIntegracion {

    private static final String LAT = "4.6097";
    private static final String LON = "-74.0817";

    @Autowired
    private RedisConnectionFactory redisFactory;

    private record Caso(String nombre, String routingKey, Message mensaje) { }

    private static Map<String, Object> base() {
        return new LinkedHashMap<>(Eventos.datos(UUID.randomUUID(), 1, "Atelopus ebenoides", "EN_PELIGRO"));
    }

    private static Caso json(String nombre, String rk, String cuerpo) {
        return new Caso(nombre, rk, Eventos.mensaje(cuerpo));
    }

    private static Caso datos(String nombre, Map<String, Object> d) {
        return json(nombre, "hallazgo.registrado", Eventos.sobre("hallazgo.registrado", d));
    }

    private static Caso conClave(String clave, Object valor) {
        Map<String, Object> d = base();
        d.put(clave, valor);
        return datos("coordenadas en clave " + clave, d);
    }

    private static Caso sin(String clave) {
        Map<String, Object> d = base();
        d.remove(clave);
        return datos("falta " + clave, d);
    }

    private static Caso con(String clave, Object valor) {
        Map<String, Object> d = base();
        d.put(clave, valor);
        return datos("valor inválido " + clave + "=" + valor, d);
    }

    private static List<Caso> invalidos() {
        List<Caso> c = new ArrayList<>();
        // obligatorios ausentes
        for (String k : List.of("hallazgoId", "version", "nombreCientifico", "fechaRecoleccion", "estadoConservacion", "departamento")) {
            c.add(sin(k));
        }
        // tipos y valores inválidos
        c.add(con("hallazgoId", "no-es-uuid"));
        c.add(con("hallazgoId", 12345));
        c.add(con("version", "1"));
        c.add(con("version", 0));
        c.add(con("version", -3));
        c.add(con("version", 1.5));
        c.add(con("version", 99999999999L));
        c.add(con("nombreCientifico", ""));
        c.add(con("nombreCientifico", "   "));
        c.add(con("nombreCientifico", "x".repeat(151)));
        c.add(con("nombreCientifico", 42));
        c.add(con("nombreCientifico", List.of("a")));
        c.add(con("fechaRecoleccion", "2026-13-45"));
        c.add(con("fechaRecoleccion", "14/03/2026"));
        c.add(con("fechaRecoleccion", 20260314));
        c.add(con("departamento", "98")); // 99 es Vichada (válido)
        c.add(con("departamento", "Narnia"));
        c.add(con("estadoConservacion", null));
        c.add(con("familia", "f".repeat(101)));
        // sobres y transporte
        c.add(json("JSON roto", "hallazgo.registrado", "{\"id\": "));
        c.add(json("cuerpo vacío", "hallazgo.registrado", ""));
        c.add(json("null", "hallazgo.registrado", "null"));
        c.add(json("arreglo", "hallazgo.registrado", "[]"));
        c.add(json("cadena", "hallazgo.registrado", "\"hola\""));
        c.add(json("sin id", "hallazgo.registrado", "{\"tipo\":\"hallazgo.registrado\",\"origen\":\"servicio-cientifico\",\"fecha\":\"2026-10-26T15:04:05Z\",\"datos\":{}}"));
        c.add(json("tipo desconocido", "hallazgo.borrado", Eventos.sobre("hallazgo.borrado", base())));
        c.add(json("tipo y routing key distintos", "hallazgo.actualizado", Eventos.sobre("hallazgo.registrado", base())));
        c.add(json("origen falso", "hallazgo.registrado", Eventos.sobre("hallazgo.registrado", "servicio-reportes", base())));
        c.add(json("origen vacío", "hallazgo.registrado", Eventos.sobre("hallazgo.registrado", "", base())));
        c.add(json("datos nulos", "hallazgo.registrado",
                "{\"id\":\"" + UUID.randomUUID() + "\",\"tipo\":\"hallazgo.registrado\",\"origen\":\"servicio-cientifico\",\"fecha\":\"2026-10-26T15:04:05Z\",\"datos\":null}"));
        c.add(json("fecha del sobre inválida", "hallazgo.registrado",
                "{\"id\":\"" + UUID.randomUUID() + "\",\"tipo\":\"hallazgo.registrado\",\"origen\":\"servicio-cientifico\",\"fecha\":\"ayer\",\"datos\":{}}"));
        Map<String, Object> grande = base();
        grande.put("descripcion", "d".repeat(70_000));
        c.add(datos("más de 64 KB", grande));
        MessageProperties p = new MessageProperties();
        p.setContentType("application/json");
        p.setUserId(co.edu.unbosque.biohub.consultapublica.soporte.Contenedores.usuarioAmqp());
        c.add(new Caso("bytes no UTF-8", "hallazgo.registrado", MessageBuilder.withBody(new byte[]{(byte) 0xff, (byte) 0xfe, 0x00, (byte) 0xc3, 0x28}).andProperties(p).build()));
        return c;
    }

    /** Eventos con coordenadas (D14): NO deben llegar a la DLQ, se descartan con ack (SEC-J-03). */
    private static List<Caso> conCoordenadas() {
        List<Caso> c = new ArrayList<>();
        // coordenadas en distintas formas y niveles (D14)
        for (String k : List.of("lat", "lon", "lng", "latitud", "longitud", "Latitude", "LONGITUDE", "LaTiTuD", "geometry",
                "geom", "ubicacionExacta", "coordinates", "coordenadas")) {
            c.add(conClave(k, k.toLowerCase().startsWith("lon") ? LON : LAT));
        }
        c.add(conClave("coordenadas", List.of(4.6097, -74.0817)));
        c.add(conClave("ubicacion", Map.of("lat", 4.6097, "lon", -74.0817)));
        c.add(conClave("extra", Map.of("anidado", Map.of("profundo", Map.of("latitud", 4.6097)))));
        c.add(conClave("lista", List.of(Map.of("longitud", -74.0817))));
        Map<String, Object> noAmen = base();
        noAmen.put("estadoConservacion", "NO_AMENAZADA");
        noAmen.put("latitud", 4.6097);
        noAmen.put("longitud", -74.0817);
        c.add(datos("NO_AMENAZADA con coordenadas", noAmen));
        // SEC-J-02: coordenadas en texto libre
        for (String campo : List.of("descripcion", "nombreComun", "tipoEspecimen")) {
            Map<String, Object> d = base();
            d.put(campo, "colectado en " + LAT + ", " + LON);
            c.add(datos("texto libre en " + campo, d));
        }
        Map<String, Object> dms = base();
        dms.put("descripcion", "4°36'35\"N 74°04'54\"W");
        c.add(datos("grados, minutos y segundos", dms));
        // transporte: cuerpo roto o routing key distinta con coordenadas
        c.add(json("JSON roto con coordenadas", "hallazgo.registrado", "{\"datos\": {\"latitud\": " + LAT + ", \"roto"));
        c.add(json("routing key distinta con coordenadas", "hallazgo.actualizado",
                Eventos.sobre("hallazgo.registrado", Map.<String, Object>of("latitud", 4.6097, "longitud", -74.0817))));
        return c;
    }

    private long enDlq() {
        Long n = rabbit.execute(ch -> ch.messageCount("consulta-publica.eventos.dlq"));
        return n == null ? 0 : n;
    }

    @Test
    void todoEventoInvalidoVaALaDlqSinCrearFichasNiFiltrarCoordenadasYLaColaSigueViva() throws Exception {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        raiz.addAppender(logs);
        List<Caso> casos = invalidos();
        try {
            List<String> noRechazados = new ArrayList<>();
            for (Caso c : casos) {
                long antes = enDlq();
                rabbit.send("biohub.eventos", c.routingKey(), c.mensaje());
                long limite = System.nanoTime() + Duration.ofSeconds(8).toNanos();
                while (enDlq() == antes && System.nanoTime() < limite) {
                    Thread.sleep(50);
                }
                if (enDlq() == antes) {
                    noRechazados.add(c.nombre());
                }
            }
            assertThat(noRechazados).as("eventos inválidos que NO llegaron a la DLQ").isEmpty();
            assertThat(enDlq()).as("mensajes en la DLQ de %d inválidos enviados", casos.size()).isEqualTo(casos.size());
            assertThat(jdbc.sql("SELECT COUNT(*) FROM ficha_publica").query(Integer.class).single())
                    .as("ninguna ficha debe crearse a partir de un evento inválido").isZero();

            // CP-21: la cola no se bloquea, un evento válido posterior se consume
            UUID ok = UUID.randomUUID();
            publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", Eventos.datos(ok, 1, "Atelopus valido", "EN_PELIGRO")));
            long l2 = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < l2 && jdbc.sql("SELECT COUNT(*) FROM ficha_publica").query(Integer.class).single() == 0) {
                Thread.sleep(100);
            }
            String ficha = mvc.perform(get("/api/publica/fichas/" + ok)).andReturn().getResponse().getContentAsString();
            assertThat(ficha).contains("Atelopus valido").doesNotContain(LAT).doesNotContain(LON);
            mvc.perform(get("/api/publica/fichas").param("q", "atelopus"));
        } finally {
            raiz.detachAppender(logs);
        }
        // Rastro de las coordenadas: logs de este servicio
        String todosLosLogs = logs.list.stream().map(ILoggingEvent::getFormattedMessage).reduce("", (a, b) -> a + "\n" + b);
        assertThat(todosLosLogs).as("logs").doesNotContain(LAT).doesNotContain(LON);
        // ... en la BD
        assertThat(jdbc.sql("SELECT CONCAT_WS('|', nombre_cientifico, nombre_comun, familia, tipo_especimen, descripcion)"
                + " FROM ficha_publica").query(String.class).list().toString()).doesNotContain(LAT).doesNotContain(LON);
        // ... y en Redis (todas las claves y valores)
        try (RedisConnection con = redisFactory.getConnection()) {
            var claves = con.keyCommands().keys("*".getBytes(StandardCharsets.UTF_8));
            assertThat(claves).as("el servicio sí cacheó algo (control)").isNotEmpty();
            for (byte[] k : claves) {
                byte[] v = con.stringCommands().get(k);
                String texto = new String(k, StandardCharsets.ISO_8859_1) + new String(v == null ? new byte[0] : v, StandardCharsets.ISO_8859_1);
                assertThat(texto).doesNotContain(LAT).doesNotContain(LON);
            }
        }
    }

    @Test
    @DisplayName("SEC-J-03: ningún evento con coordenadas llega a la DLQ ni deja rastro en BD, Redis ni logs (RNF9, ASR6)")
    void eventosConCoordenadasSeDescartanConAckSinRastro() throws Exception {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> logs = new ListAppender<>();
        logs.start();
        raiz.addAppender(logs);
        List<Caso> casos = conCoordenadas();
        try {
            for (Caso c : casos) {
                rabbit.send("biohub.eventos", c.routingKey(), c.mensaje());
            }
            // centinela válido: al verse la ficha, los anteriores (misma cola FIFO) ya se procesaron
            UUID ok = UUID.randomUUID();
            publicar("hallazgo.registrado", Eventos.sobre("hallazgo.registrado", Eventos.datos(ok, 1, "Atelopus valido", "EN_PELIGRO")));
            long limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
            while (System.nanoTime() < limite && jdbc.sql("SELECT COUNT(*) FROM ficha_publica").query(Integer.class).single() == 0) {
                Thread.sleep(100);
            }
            Thread.sleep(1_500); // margen para que un eventual dead-letter llegara a la DLQ
            mvc.perform(get("/api/publica/fichas/" + ok));
            mvc.perform(get("/api/publica/fichas").param("q", "atelopus"));
        } finally {
            raiz.detachAppender(logs);
        }
        assertThat(enDlq()).as("la DLQ no debe retener coordenadas (%d casos)", casos.size()).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM ficha_publica").query(Integer.class).single())
                .as("solo la ficha del evento válido").isEqualTo(1);
        List<String> mensajes = logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(mensajes.stream().filter(m -> m.contains("INCIDENTE SEC-J-03")).count())
                .as("un incidente por evento descartado").isEqualTo(casos.size());
        String todosLosLogs = String.join("\n", mensajes);
        assertThat(todosLosLogs).doesNotContain(LAT).doesNotContain(LON).doesNotContain("4°36");
        assertThat(jdbc.sql("SELECT CONCAT_WS('|', nombre_cientifico, nombre_comun, familia, tipo_especimen, descripcion)"
                + " FROM ficha_publica").query(String.class).list().toString()).doesNotContain(LAT).doesNotContain(LON);
        try (RedisConnection con = redisFactory.getConnection()) {
            for (byte[] k : con.keyCommands().keys("*".getBytes(StandardCharsets.UTF_8))) {
                byte[] v = con.stringCommands().get(k);
                String texto = new String(k, StandardCharsets.ISO_8859_1) + new String(v == null ? new byte[0] : v, StandardCharsets.ISO_8859_1);
                assertThat(texto).doesNotContain(LAT).doesNotContain(LON);
            }
        }
    }
}
