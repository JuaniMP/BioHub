package co.edu.unbosque.biohub.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.gateway.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Atrapa todo /api/** y lo reenvía. Las rutas propias del gateway (p. ej. /api/gateway/ping)
 * son más específicas y Spring las resuelve antes que este comodín.
 * El cuerpo NO se lee con @RequestBody: ProxyService lo lee con un límite de tamaño.
 */
@RestController
public class ProxyController {

    private final ProxyService proxyService;

    public ProxyController(ProxyService proxyService) {
        this.proxyService = proxyService;
    }

    @RequestMapping("/api/**")
    public ResponseEntity<byte[]> reenviar(HttpServletRequest request) {
        return proxyService.reenviar(request);
    }
}
