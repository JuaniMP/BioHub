package co.edu.unbosque.biohub.cientifico.ubicacion.controller;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.cientifico.ubicacion.service.MapaService;

/** HU-07: mapa con coordenadas exactas, solo cientifico_alto_rango. */
@RestController
@RequestMapping("/api/cientifico/ubicacion")
public class MapaUbicacionController {

    private final MapaService servicio;

    public MapaUbicacionController(MapaService servicio) {
        this.servicio = servicio;
    }

    @GetMapping("/mapa")
    @PreAuthorize("@politicaUbicacion.puedeVerCoordenadas(authentication)")
    public ResponseEntity<MapaResponse> mapa(
            @RequestParam(required = false) String especie,
            @RequestParam(required = false) String departamento,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String hallazgoId,
            Authentication autenticacion) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(servicio.mapa(autenticacion, especie, departamento, desde, hasta, hallazgoId));
    }
}
