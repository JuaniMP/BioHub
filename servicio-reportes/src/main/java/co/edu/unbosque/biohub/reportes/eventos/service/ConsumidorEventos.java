package co.edu.unbosque.biohub.reportes.eventos.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.reportes.eventos.domain.EventoSobre;

/**
 * Consume la cola de eventos de este servicio. Valida el sobre; si es inválido lo rechaza sin reencolar
 * (el broker lo envía a la DLQ). En el log solo se registran id, tipo y origen (nunca los datos).
 */
@Component
public class ConsumidorEventos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);

    /** Tipos conocidos. Los eventos reales (hallazgo.*) se agregan aquí al implementarlos. */
    static final Set<String> TIPOS_CONOCIDOS = Set.of("prueba.ping", "hallazgo.registrado");
    static final int TAMANO_MAXIMO_BYTES = 64 * 1024;
    static final int MENSAJE_PRUEBA_MAX = 200;

    private final ObjectMapper objectMapper;
    private final AlmacenEventosRecibidos almacen;

    public ConsumidorEventos(ObjectMapper objectMapper, AlmacenEventosRecibidos almacen) {
        this.objectMapper = objectMapper;
        this.almacen = almacen;
    }

    @RabbitListener(queues = "${biohub.eventos.cola}")
    public void recibir(Message mensaje) {
        EventoSobre evento = interpretar(mensaje);
        almacen.agregar(evento);
        log.info("Evento recibido id={} tipo={} origen={}", evento.id(), evento.tipo(), evento.origen());
    }

    EventoSobre interpretar(Message mensaje) {
        byte[] cuerpo = mensaje.getBody();
        if (cuerpo == null || cuerpo.length == 0 || cuerpo.length > TAMANO_MAXIMO_BYTES) {
            throw new EventoInvalidoException("Evento con tamaño no permitido");
        }
        EventoSobre evento;
        try {
            evento = objectMapper.readValue(cuerpo, EventoSobre.class);
        } catch (IOException | RuntimeException e) {
            throw new EventoInvalidoException("Evento con JSON inválido", e);
        }
        if (evento == null || evento.id() == null || evento.fecha() == null
                || esVacio(evento.tipo()) || esVacio(evento.origen())) {
            throw new EventoInvalidoException("Evento con sobre incompleto");
        }
        if (!TIPOS_CONOCIDOS.contains(evento.tipo())) {
            throw new EventoInvalidoException("Evento de tipo desconocido");
        }
        String rk = mensaje.getMessageProperties().getReceivedRoutingKey();
        if (rk != null && !rk.equals(evento.tipo())) {
            throw new EventoInvalidoException("Routing key distinta del tipo del evento");
        }
        if (evento.tipo().startsWith("prueba.")) {
            validarPrueba(evento);
        }
        return evento.datos() == null
                ? new EventoSobre(evento.id(), evento.tipo(), evento.origen(), evento.fecha(), evento.usuario(), Map.of())
                : evento;
    }

    private static void validarPrueba(EventoSobre evento) {
        Object m = evento.datos() == null ? null : evento.datos().get("mensaje");
        if (!(m instanceof String texto) || texto.length() > MENSAJE_PRUEBA_MAX) {
            throw new EventoInvalidoException("Evento de prueba con datos inválidos");
        }
    }

    private static boolean esVacio(String s) {
        return s == null || s.isBlank();
    }
}
