package co.edu.unbosque.biohub.cientifico.eventos.service;

/** El broker no está disponible o no confirmó el evento (→ 503). No transporta detalles internos. */
public class EventoNoPublicadoException extends RuntimeException {

    public EventoNoPublicadoException(Throwable causa) {
        super("Broker de mensajes no disponible", causa);
    }
}
