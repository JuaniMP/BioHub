package co.edu.unbosque.biohub.cientifico.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.core.MessageProperties;
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
}
