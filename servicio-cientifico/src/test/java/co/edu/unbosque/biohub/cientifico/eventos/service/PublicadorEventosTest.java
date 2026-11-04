package co.edu.unbosque.biohub.cientifico.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import co.edu.unbosque.biohub.cientifico.eventos.domain.EventoSobre;

class PublicadorEventosTest {

    private static final Instant AHORA = Instant.parse("2026-10-21T12:00:00Z");

    private RabbitTemplate template;
    private RabbitOperations operaciones;
    private PublicadorEventos publicador;

    @BeforeEach
    void preparar() {
        template = mock(RabbitTemplate.class);
        ConnectionFactory fabrica = mock(ConnectionFactory.class);
        when(fabrica.getUsername()).thenReturn("cientifico_mq");
        when(template.getConnectionFactory()).thenReturn(fabrica);
        operaciones = mock(RabbitOperations.class);
        when(template.invoke(any(RabbitOperations.OperationsCallback.class))).thenAnswer(inv -> {
            RabbitOperations.OperationsCallback<?> cb = inv.getArgument(0);
            return cb.doInRabbit(operaciones);
        });
        publicador = new PublicadorEventos(template, "biohub.eventos", Duration.ofSeconds(5),
                Clock.fixed(AHORA, ZoneOffset.UTC));
    }

    @Test
    void construyeElSobreYUsaElTipoComoRoutingKey() {
        EventoSobre evento = publicador.publicar("prueba.ping", "admin1", Map.of("mensaje", "hola"));

        assertThat(evento.id()).isNotNull();
        assertThat(evento.tipo()).isEqualTo("prueba.ping");
        assertThat(evento.origen()).isEqualTo("servicio-cientifico");
        assertThat(evento.fecha()).isEqualTo(AHORA);
        assertThat(evento.usuario()).isEqualTo("admin1");
        assertThat(evento.datos()).containsEntry("mensaje", "hola");
        verify(operaciones).convertAndSend(eq("biohub.eventos"), eq("prueba.ping"), eq(evento),
                any(MessagePostProcessor.class));
        verify(operaciones).waitForConfirmsOrDie(5000L);
    }

    @Test
    void elMensajeEsPersistenteYLlevaElIdComoMessageId() throws Exception {
        EventoSobre evento = publicador.publicar("prueba.ping", "admin1", Map.of("mensaje", "x"));

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(operaciones).convertAndSend(eq("biohub.eventos"), eq("prueba.ping"), eq(evento), captor.capture());
        Message mensaje = new Message(new byte[0], new MessageProperties());
        captor.getValue().postProcessMessage(mensaje);

        assertThat(mensaje.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
        assertThat(mensaje.getMessageProperties().getMessageId()).isEqualTo(evento.id().toString());
    }

    @Test
    void elMensajeFijaElUserIdConElUsuarioAmqpDeLaConexion() throws Exception {
        EventoSobre evento = publicador.publicar("hallazgo.registrado", "admin1", Map.of());

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(operaciones).convertAndSend(eq("biohub.eventos"), eq("hallazgo.registrado"), eq(evento),
                captor.capture());
        Message mensaje = new Message(new byte[0], new MessageProperties());
        captor.getValue().postProcessMessage(mensaje);

        assertThat(mensaje.getMessageProperties().getUserId()).isEqualTo("cientifico_mq");
    }

    @Test
    void elUserIdSigueAlUsuarioConfiguradoYNoEsUnaConstante() throws Exception {
        ConnectionFactory otra = mock(ConnectionFactory.class);
        when(otra.getUsername()).thenReturn("otro_usuario");
        when(template.getConnectionFactory()).thenReturn(otra);
        PublicadorEventos otroPublicador = new PublicadorEventos(template, "biohub.eventos",
                Duration.ofSeconds(5), Clock.fixed(AHORA, ZoneOffset.UTC));

        EventoSobre evento = otroPublicador.publicar("hallazgo.registrado", "admin1", Map.of());

        ArgumentCaptor<MessagePostProcessor> captor = ArgumentCaptor.forClass(MessagePostProcessor.class);
        verify(operaciones).convertAndSend(eq("biohub.eventos"), eq("hallazgo.registrado"), eq(evento),
                captor.capture());
        Message mensaje = new Message(new byte[0], new MessageProperties());
        captor.getValue().postProcessMessage(mensaje);
        assertThat(mensaje.getMessageProperties().getUserId()).isEqualTo("otro_usuario");
    }

    @Test
    void sinUsuarioAmqpDeterminableFallaAlConstruir() {
        when(template.getConnectionFactory()).thenReturn(null);

        assertThatThrownBy(() -> new PublicadorEventos(template, "biohub.eventos", Duration.ofSeconds(5),
                Clock.fixed(AHORA, ZoneOffset.UTC))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void siElBrokerNoEstaDisponibleLanzaEventoNoPublicado() {
        when(template.invoke(any(RabbitOperations.OperationsCallback.class)))
                .thenThrow(new AmqpConnectException(new RuntimeException("conexion rechazada 10.0.0.5")));

        assertThatThrownBy(() -> publicador.publicar("prueba.ping", "admin1", Map.of()))
                .isInstanceOf(EventoNoPublicadoException.class);
    }

    @Test
    void siElBrokerNoConfirmaLanzaEventoNoPublicado() {
        doThrow(new org.springframework.amqp.AmqpException("nack")).when(operaciones).waitForConfirmsOrDie(anyLong());

        assertThatThrownBy(() -> publicador.publicar("prueba.ping", "admin1", Map.of()))
                .isInstanceOf(EventoNoPublicadoException.class);
    }

    // ---- SEC-J-13 / D46: hallazgo.* nunca lleva coordenadas ----

    @Test
    void hallazgoConClaveDeCoordenadasSeRechazaSinPublicar() {
        for (String clave : new String[] {"latitud", "Longitud", "lat", "lng", "coordenadas", "geometry"}) {
            assertThatThrownBy(() -> publicador.publicar("hallazgo.registrado", "admin1", Map.of(clave, 5.5)))
                    .as(clave).isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("hallazgo.* no puede llevar coordenadas (D14)");
        }
        verify(template, never()).invoke(any(RabbitOperations.OperationsCallback.class));
    }

    @Test
    void hallazgoConCoordenadasAnidadasSeRechaza() {
        Map<String, Object> datos = Map.of("detalle", Map.of("sitio", List.of(Map.of("Latitud", 5.5))));

        assertThatThrownBy(() -> publicador.publicar("hallazgo.reclasificado", "admin1", datos))
                .isInstanceOf(IllegalArgumentException.class);
        verify(template, never()).invoke(any(RabbitOperations.OperationsCallback.class));
    }

    @Test
    void hallazgoConCoordenadasEnTextoSeRechazaYElMensajeNoFiltraElValor() {
        for (String texto : List.of("colectado en 5.53214, -73.36712", "5°31'55\"N 73°22'02\"W", "lat: 5.53 lon: -73.36")) {
            assertThatThrownBy(() -> publicador.publicar("hallazgo.registrado", "admin1",
                    Map.of("descripcion", texto)))
                    .as(texto).isInstanceOf(IllegalArgumentException.class)
                    .satisfies(e -> assertThat(e.getMessage()).doesNotContain("5.53"));
        }
        verify(template, never()).invoke(any(RabbitOperations.OperationsCallback.class));
    }

    @Test
    void hallazgoSinCoordenadasSePublica() {
        EventoSobre evento = publicador.publicar("hallazgo.registrado", "admin1", Map.of(
                "hallazgoId", "7b1e0d0e-5d6c-4b0e-9a55-1f2a6f0c1111", "version", 1,
                "nombreCientifico", "Atelopus ebenoides", "fechaRecoleccion", "2026-03-14",
                "estadoConservacion", "EN_PELIGRO", "departamento", "15",
                "descripcion", "Anfibio de páramo, altitud entre 3.200, 3.500 msnm, medía 12,5 cm"));

        assertThat(evento.tipo()).isEqualTo("hallazgo.registrado");
        verify(operaciones).waitForConfirmsOrDie(5000L);
    }

    @Test
    void otrosTiposNoSeRevisanPorEstaGuarda() {
        // La guarda es solo para hallazgo.*: "prueba.ping" conserva el comportamiento anterior
        EventoSobre evento = publicador.publicar("prueba.ping", "admin1", Map.of("lat", 5.5));

        assertThat(evento.tipo()).isEqualTo("prueba.ping");
    }
}
