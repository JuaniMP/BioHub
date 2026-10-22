package co.edu.unbosque.biohub.cientifico.eventos.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.cientifico.eventos.domain.EventoSobre;
import co.edu.unbosque.biohub.cientifico.eventos.service.PublicadorEventos;
import jakarta.validation.Valid;

/** Publica el evento de prueba "prueba.ping" para verificar RabbitMQ de punta a punta. */
@RestController
@RequestMapping("/api/cientifico/eventos")
public class EventosPruebaController {

    static final String TIPO_PRUEBA = "prueba.ping";
    static final String MENSAJE_POR_DEFECTO = "ping";

    private final PublicadorEventos publicador;

    public EventosPruebaController(PublicadorEventos publicador) {
        this.publicador = publicador;
    }

    @PostMapping("/prueba")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<EventoSobre> publicarPrueba(@Valid @RequestBody(required = false) EventoPruebaRequest cuerpo,
                                                      Authentication autenticacion) {
        String mensaje = cuerpo == null || cuerpo.mensaje() == null || cuerpo.mensaje().isBlank()
                ? MENSAJE_POR_DEFECTO : cuerpo.mensaje();
        EventoSobre evento = publicador.publicar(TIPO_PRUEBA, autenticacion.getName(), Map.of("mensaje", mensaje));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(evento);
    }
}
