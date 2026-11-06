package co.edu.unbosque.biohub.gateway.infrastructure.filtro;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import co.edu.unbosque.biohub.gateway.domain.RegistroRevocaciones;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Hace efectivo un cambio de rol desde la siguiente petición (RF19, HU-10 c.3). Un JWT sigue siendo
 * válido hasta que vence, así que este filtro rechaza los emitidos antes del cambio. Va después de
 * validar el JWT: solo consulta Redis para tokens auténticos.
 */
@Component
public class RevocacionTokenFilter extends OncePerRequestFilter {

    private final RegistroRevocaciones revocaciones;

    public RevocacionTokenFilter(RegistroRevocaciones revocaciones) {
        this.revocaciones = revocaciones;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion instanceof JwtAuthenticationToken jwtAutenticado && revocado(jwtAutenticado.getToken())) {
            SecurityContextHolder.clearContext();
            respuesta.setStatus(HttpStatus.UNAUTHORIZED.value());
            respuesta.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer error=\"invalid_token\"");
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private boolean revocado(Jwt token) {
        return token.getIssuedAt() != null && revocaciones.revocado(token.getSubject(), token.getIssuedAt());
    }
}
