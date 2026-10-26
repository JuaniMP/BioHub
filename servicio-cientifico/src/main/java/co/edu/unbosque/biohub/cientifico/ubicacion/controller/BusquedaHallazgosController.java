package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.cientifico.ubicacion.service.BusquedaHallazgosService;

/** HU-13: búsqueda interna. Solo alto rango recibe latitud/longitud (lo decide el servicio). */
@RestController
@RequestMapping("/api/cientifico/ubicacion")
public class BusquedaHallazgosController {

    private final BusquedaHallazgosService servicio;

    public BusquedaHallazgosController(BusquedaHallazgosService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/hallazgos")
    @PreAuthorize("hasAnyRole('investigador_campo','auditor_cientifico','cientifico_alto_rango','analista_ambiental','administrador')")
    public ResponseEntity<PaginaHallazgosResponse> buscar(
            @RequestParam(required = false) String especie,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String investigador,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String bbox,
            Authentication autenticacion) {
        PaginaHallazgosResponse respuesta = servicio.buscar(autenticacion, especie, desde, hasta, investigador,
                departamento, page, size, bbox);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(respuesta);
    }
}
