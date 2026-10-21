package co.edu.unbosque.biohub.gateway.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ping propio del gateway (no se reenvía). */
@RestController
public class PingController {

    static final String SERVICIO = "api-gateway";

    @GetMapping("/api/gateway/ping")
    public PingResponse ping(Authentication autenticacion) {
        List<String> roles = autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .sorted()
                .toList();
        return new PingResponse(SERVICIO, autenticacion.getName(), roles, Instant.now());
    }
}
