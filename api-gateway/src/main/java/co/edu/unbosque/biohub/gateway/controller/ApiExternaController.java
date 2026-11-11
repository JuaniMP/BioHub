package co.edu.unbosque.biohub.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.gateway.config.RutasGateway;
import co.edu.unbosque.biohub.gateway.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * API para consumidores externos (HU-08, D16). Estas URL son el contrato con universidades y ONG: no
 * cambian aunque los servicios internos cambien de lugar (ASR9, RNF12).
 */
@RestController
public class ApiExternaController {

    private final ProxyService proxyService;

    public ApiExternaController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @GetMapping({RutasGateway.PREFIJO_API_EXTERNA + RutasGateway.RECURSO_FICHAS,
            RutasGateway.PREFIJO_API_EXTERNA + RutasGateway.RECURSO_FICHAS + "/{id}"})
    public ResponseEntity<byte[]> reenviar(HttpServletRequest request) {
        return proxyService.reenviarApiExterna(request);
    }
}
