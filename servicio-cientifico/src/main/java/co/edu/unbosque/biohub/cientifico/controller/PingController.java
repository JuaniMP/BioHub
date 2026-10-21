package co.edu.unbosque.biohub.cientifico.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cientifico")
public class PingController {

    static final String SERVICIO = "servicio-cientifico";

    @GetMapping("/ping")
    public PingResponse ping(Authentication autenticacion) {
        if (autenticacion == null || autenticacion instanceof AnonymousAuthenticationToken) {
            return new PingResponse(SERVICIO, "anonimo", List.of(), Instant.now());
        }
        List<String> roles = autenticacion.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.startsWith("ROLE_") ? a.substring(5) : a)
                .sorted()
                .toList();
        return new PingResponse(SERVICIO, autenticacion.getName(), roles, Instant.now());
    }
}
