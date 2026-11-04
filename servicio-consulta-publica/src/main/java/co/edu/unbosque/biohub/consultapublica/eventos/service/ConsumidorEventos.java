package co.edu.unbosque.biohub.consultapublica.eventos.service;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.consultapublica.eventos.domain.EventoSobre;

/**
 * Consume la cola de eventos de este servicio. Valida el sobre; si es inválido lo rechaza sin reencolar
 * (el broker lo envía a la DLQ). En el log solo se registran id, tipo y origen (nunca los datos).
 */
@Component
public class ConsumidorEventos {

    private static final Logger log = LoggerFactory.getLogger(ConsumidorEventos.class);

    /** Tipos conocidos: la prueba y los tres eventos reales hallazgo.* (contrato 04, §7). */
    static final Set<String> TIPOS_CONOCIDOS = Set.of("prueba.ping", "hallazgo.registrado",
            "hallazgo.reclasificado", "hallazgo.actualizado");
    static final String ORIGEN_CIENTIFICO = "servicio-cientifico";
    static final int TAMANO_MAXIMO_BYTES = 64 * 1024;
    static final int MENSAJE_PRUEBA_MAX = 200;

    private final ObjectMapper objectMapper;
    private final AlmacenEventosRecibidos almacen;
    private final ProcesadorHallazgos procesador;
    private final boolean verificarPublicador;
    private final String publicadorEsperado;

    public ConsumidorEventos(ObjectMapper objectMapper, AlmacenEventosRecibidos almacen,
                             ProcesadorHallazgos procesador,
                             @Value("${biohub.eventos.verificar-publicador:true}") boolean verificarPublicador,
                             @Value("${biohub.eventos.publicador-cientifico:cientifico_mq}") String publicadorEsperado) {
        // SEC-J-01: una clave repetida en el JSON (p. ej. dos "version") es ambigua y se rechaza
        this.objectMapper = objectMapper.copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        this.almacen = almacen;
        this.procesador = procesador;
        this.verificarPublicador = verificarPublicador;
        this.publicadorEsperado = publicadorEsperado;
        if (!verificarPublicador) {
            log.warn("SEC-J-01: la verificación del publicador AMQP (BIOHUB_EVENTOS_VERIFICAR_PUBLICADOR) está "
                    + "DESACTIVADA: solo es aceptable en local/pruebas; en un despliegue real debe ser true");
        }
    }

    /**
     * Punto de entrada. Un evento con coordenadas (en una clave o en texto libre) se DESCARTA
     * con ack y se registra un incidente sin valores: NO va a la DLQ, que lo retendría en claro y sin TTL
     * (SEC-J-03, RNF9/ASR6). Cualquier otro rechazo sigue el camino normal hacia la DLQ.
     */
    @RabbitListener(queues = "${biohub.eventos.cola}")
    public void recibir(Message mensaje) {
        try {
            // Se analizan todos los mensajes, no solo los de routing key hallazgo.*: un cuerpo hallazgo.* publicado con
            // otra routing key también terminaría en la DLQ
            if (traeCoordenadas(mensaje.getBody())) {
                throw new EventoConCoordenadasException();
            }
            procesar(mensaje);
        } catch (EventoConCoordenadasException e) {
            registrarIncidente(mensaje);
        }
    }

    /** Analiza el cuerpo: estructurado si es JSON válido, en bruto si no. Nunca lanza ni registra valores. */
    private boolean traeCoordenadas(byte[] cuerpo) {
        if (cuerpo == null || cuerpo.length == 0) {
            return false;
        }
        if (cuerpo.length <= DetectorCoordenadas.MAXIMO_BYTES_ANALIZADOS) {
            try {
                return DetectorCoordenadas.contiene(objectMapper.readValue(cuerpo, Object.class));
            } catch (IOException | RuntimeException e) {
                // JSON roto o no UTF-8: se cae al análisis en bruto
            }
        }
        return DetectorCoordenadas.contieneEnBruto(cuerpo);
    }

    private void registrarIncidente(Message mensaje) {
        String id = "desconocido";
        String tipo = String.valueOf(mensaje.getMessageProperties().getReceivedRoutingKey())
                .replaceAll("\\p{Cntrl}", "_");
        tipo = tipo.length() > 60 ? tipo.substring(0, 60) : tipo;
        try {
            EventoSobre e = objectMapper.readValue(mensaje.getBody(), EventoSobre.class);
            if (e != null && e.id() != null) {
                id = e.id().toString();
            }
        } catch (IOException | RuntimeException ignorado) {
            // sobre ilegible: el incidente se registra sin id
        }
        log.error("INCIDENTE SEC-J-03: evento con coordenadas DESCARTADO (ack, sin DLQ) id={} tipo={}. "
                + "Avisar al responsable del publicador; no se registran los valores", id, tipo);
    }

    private void procesar(Message mensaje) {
        EventoSobre evento = interpretar(mensaje);
        log.info("Evento recibido id={} tipo={} origen={}", evento.id(), evento.tipo(), evento.origen());
        if (evento.tipo().startsWith("hallazgo.")) {
            validarOrigen(evento, mensaje);
            procesador.procesar(evento);
        } else {
            almacen.agregar(evento);
        }
    }

    /** D24: solo servicio-cientifico publica hallazgo.*; opcionalmente se verifica el user_id AMQP (SEC-D-03). */
    private void validarOrigen(EventoSobre evento, Message mensaje) {
        if (!ORIGEN_CIENTIFICO.equals(evento.origen())) {
            log.warn("Evento hallazgo.* con origen no permitido id={}", evento.id());
            throw new EventoInvalidoException("Evento con origen no permitido");
        }
        if (verificarPublicador
                && !publicadorEsperado.equals(mensaje.getMessageProperties().getReceivedUserId())) {
            log.warn("Evento hallazgo.* con publicador no permitido id={}", evento.id());
            throw new EventoInvalidoException("Evento con publicador no permitido");
        }
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
            throw new EventoInvalidoException("Evento con JSON inválido"); // sin la causa: Jackson puede citar el contenido
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
