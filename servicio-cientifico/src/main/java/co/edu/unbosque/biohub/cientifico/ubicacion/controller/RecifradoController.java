package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.cientifico.ubicacion.service.RecifradoService;

/** Re-cifrado tras rotar la llave. Rol administrador; nunca devuelve coordenadas. */
@RestController
@RequestMapping("/api/cientifico/ubicacion")
public class RecifradoController {

    private final RecifradoService servicio;

    public RecifradoController(RecifradoService servicio) {
        this.servicio = servicio;
    }

    @PostMapping("/recifrado")
    @PreAuthorize("hasRole('administrador')")
    public ResponseEntity<RecifradoResponse> recifrar(Authentication autenticacion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(servicio.recifrar(autenticacion.getName()));
    }
}
