package co.edu.unbosque.biohub.consultapublica.eventos.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.consultapublica.eventos.domain.EventoSobre;
import co.edu.unbosque.biohub.consultapublica.eventos.service.AlmacenEventosRecibidos;

/** Verificación del flujo de eventos: lista los últimos 20 eventos recibidos. Solo administrador. */
@RestController
@RequestMapping("/api/publica/eventos")
public class EventosRecibidosController {

    private final AlmacenEventosRecibidos almacen;
    private final String servicio;

    public EventosRecibidosController(AlmacenEventosRecibidos almacen,
                                      @Value("${spring.application.name}") String servicio) {
        this.almacen = almacen;
        this.servicio = servicio;
    }

    @GetMapping("/recibidos")
    @PreAuthorize("hasRole('administrador')")
    public EventosRecibidosResponse recibidos() {
        List<EventoSobre> eventos = almacen.ultimos();
        return new EventosRecibidosResponse(servicio, eventos.size(), eventos);
    }
}
