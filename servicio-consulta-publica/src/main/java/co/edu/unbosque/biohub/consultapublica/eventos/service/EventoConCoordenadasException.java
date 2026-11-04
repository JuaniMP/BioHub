package co.edu.unbosque.biohub.consultapublica.eventos.service;

/**
 * El evento {@code hallazgo.*} trae coordenadas (en una clave o en texto libre). Subclase de
 * {@link EventoInvalidoException} para que cualquier otro llamador lo siga tratando como inválido, pero
 * {@link ConsumidorEventos} lo DESCARTA con ack y registra un incidente: nunca debe llegar a la DLQ, que es un
 * almacén persistente fuera del cifrado (SEC-J-03, RNF9/ASR6). El mensaje no incluye ningún valor del evento.
 */
public class EventoConCoordenadasException extends EventoInvalidoException {

    public EventoConCoordenadasException() {
        super("Evento con coordenadas no permitido");
    }
}
