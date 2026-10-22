package co.edu.unbosque.biohub.reportes.eventos.service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.reportes.eventos.domain.EventoSobre;

/** Guarda en memoria los últimos {@value #CAPACIDAD} eventos recibidos (solo para verificación). */
@Component
public class AlmacenEventosRecibidos {

    static final int CAPACIDAD = 20;

    private final Deque<EventoSobre> eventos = new ArrayDeque<>();

    public synchronized void agregar(EventoSobre evento) {
        eventos.addFirst(evento);
        while (eventos.size() > CAPACIDAD) {
            eventos.removeLast();
        }
    }

    /** Del más reciente al más antiguo. */
    public synchronized List<EventoSobre> ultimos() {
        return List.copyOf(eventos);
    }
}
