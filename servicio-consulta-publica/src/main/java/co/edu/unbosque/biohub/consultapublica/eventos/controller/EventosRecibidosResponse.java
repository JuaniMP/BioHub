package co.edu.unbosque.biohub.consultapublica.eventos.controller;

import java.util.List;

import co.edu.unbosque.biohub.consultapublica.eventos.domain.EventoSobre;

/** Últimos eventos recibidos por este servicio, del más reciente al más antiguo. */
public record EventosRecibidosResponse(String servicio, int total, List<EventoSobre> eventos) {
}
