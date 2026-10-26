package co.edu.unbosque.biohub.consultapublica.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

class ConsumidorEventosTest {

    private AlmacenEventosRecibidos almacen;
    private ConsumidorEventos consumidor;
    private ProcesadorHallazgos procesador;

    @BeforeEach
    void preparar() {
        almacen = new AlmacenEventosRecibidos();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        procesador = org.mockito.Mockito.mock(ProcesadorHallazgos.class);
        consumidor = new ConsumidorEventos(mapper, almacen, procesador, false, "cientifico_mq");
    }

    private static Message mensaje(String json, String routingKey) {
        MessageProperties props = new MessageProperties();
        props.setReceivedRoutingKey(routingKey);
        return new Message(json.getBytes(StandardCharsets.UTF_8), props);
    }

    private static String json(String tipo, String datos) {
        return """
                {"id":"%s","tipo":"%s","origen":"servicio-cientifico","fecha":"2026-10-21T12:00:00Z",
                 "usuario":"admin1","datos":%s}""".formatted(UUID.randomUUID(), tipo, datos);
    }

    @Test
    void almacenaUnEventoValido() {
        consumidor.recibir(mensaje(json("prueba.ping", "{\"mensaje\":\"hola\"}"), "prueba.ping"));

        assertThat(almacen.ultimos()).hasSize(1);
        assertThat(almacen.ultimos().get(0).tipo()).isEqualTo("prueba.ping");
        assertThat(almacen.ultimos().get(0).datos()).containsEntry("mensaje", "hola");
    }

    @Test
    void conservaSoloLosUltimos20DelMasRecienteAlMasAntiguo() {
        for (int i = 0; i < 25; i++) {
            consumidor.recibir(mensaje(json("prueba.ping", "{\"mensaje\":\"m" + i + "\"}"), "prueba.ping"));
        }
        assertThat(almacen.ultimos()).hasSize(20);
        assertThat(almacen.ultimos().get(0).datos()).containsEntry("mensaje", "m24");
        assertThat(almacen.ultimos().get(19).datos()).containsEntry("mensaje", "m5");
    }

    @Test
    void despachaLosTresTiposHallazgoAlProcesadorSinGuardarlosEnElAlmacenDePrueba() {
        for (String tipo : new String[] {"hallazgo.registrado", "hallazgo.reclasificado", "hallazgo.actualizado"}) {
            consumidor.recibir(mensaje(json(tipo, "{\"x\":1}"), tipo));
        }
        org.mockito.Mockito.verify(procesador, org.mockito.Mockito.times(3))
                .procesar(org.mockito.ArgumentMatchers.any());
        assertThat(almacen.ultimos()).isEmpty();
    }

    @Test
    void rechazaHallazgoConOrigenDistintoDeServicioCientifico() {
        String falso = json("hallazgo.registrado", "{\"x\":1}").replace("\"origen\":\"servicio-cientifico\"", "\"origen\":\"otro-servicio\"");
        assertThatThrownBy(() -> consumidor.recibir(mensaje(falso, "hallazgo.registrado")))
                .isInstanceOf(EventoInvalidoException.class);
        org.mockito.Mockito.verifyNoInteractions(procesador);
    }

    @Test
    void conVerificacionActivaExigeElUserIdDelPublicador() {
        ConsumidorEventos estricto = new ConsumidorEventos(new ObjectMapper().registerModule(new JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false),
                almacen, procesador, true, "cientifico_mq");
        MessageProperties intruso = new MessageProperties();
        intruso.setReceivedRoutingKey("hallazgo.registrado");
        intruso.setReceivedUserId("otro_mq");
        String cuerpo = json("hallazgo.registrado", "{\"x\":1}");
        assertThatThrownBy(() -> estricto.recibir(new Message(cuerpo.getBytes(StandardCharsets.UTF_8), intruso)))
                .isInstanceOf(EventoInvalidoException.class);

        MessageProperties legitimo = new MessageProperties();
        legitimo.setReceivedRoutingKey("hallazgo.registrado");
        legitimo.setReceivedUserId("cientifico_mq");
        estricto.recibir(new Message(cuerpo.getBytes(StandardCharsets.UTF_8), legitimo));
        org.mockito.Mockito.verify(procesador).procesar(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rechazaJsonRotoSinAlmacenar() {
        assertThatThrownBy(() -> consumidor.recibir(mensaje("{roto", "prueba.ping")))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        assertThat(almacen.ultimos()).isEmpty();
    }

    @Test
    void rechazaTipoDesconocido() {
        assertThatThrownBy(() -> consumidor.recibir(mensaje(json("prueba.otro", "{\"mensaje\":\"a\"}"), "prueba.otro")))
                .isInstanceOf(EventoInvalidoException.class);
        assertThat(almacen.ultimos()).isEmpty();
    }

    @Test
    void rechazaSobreIncompleto() {
        assertThatThrownBy(() -> consumidor.recibir(mensaje("{\"tipo\":\"prueba.ping\"}", "prueba.ping")))
                .isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void rechazaRoutingKeyDistintaDelTipo() {
        assertThatThrownBy(() -> consumidor.recibir(
                mensaje(json("prueba.ping", "{\"mensaje\":\"a\"}"), "hallazgo.registrado")))
                .isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void rechazaPruebaSinMensajeOMuyLargo() {
        assertThatThrownBy(() -> consumidor.recibir(mensaje(json("prueba.ping", "{}"), "prueba.ping")))
                .isInstanceOf(EventoInvalidoException.class);
        assertThatThrownBy(() -> consumidor.recibir(
                mensaje(json("prueba.ping", "{\"mensaje\":\"" + "a".repeat(201) + "\"}"), "prueba.ping")))
                .isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void rechazaCuerpoVacioOEnorme() {
        assertThatThrownBy(() -> consumidor.recibir(mensaje("", "prueba.ping")))
                .isInstanceOf(EventoInvalidoException.class);
        assertThatThrownBy(() -> consumidor.recibir(mensaje("x".repeat(70_000), "prueba.ping")))
                .isInstanceOf(EventoInvalidoException.class);
    }
}
