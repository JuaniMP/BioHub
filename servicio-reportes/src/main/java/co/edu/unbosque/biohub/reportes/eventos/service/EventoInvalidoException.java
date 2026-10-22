package co.edu.unbosque.biohub.reportes.eventos.service;

import org.springframework.amqp.AmqpRejectAndDontRequeueException;

/** Mensaje malformado o de tipo desconocido: se rechaza sin reencolar y no se reintenta (va a la DLQ). */
public class EventoInvalidoException extends AmqpRejectAndDontRequeueException {

    public EventoInvalidoException(String motivo) {
        super(motivo);
    }

    public EventoInvalidoException(String motivo, Throwable causa) {
        super(motivo, causa);
    }
}
