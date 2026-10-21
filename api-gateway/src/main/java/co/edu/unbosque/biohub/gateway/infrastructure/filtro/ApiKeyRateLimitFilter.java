package co.edu.unbosque.biohub.gateway.infrastructure.filtro;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Punto de extensión para API Keys y rate limiting de la API pública.
 *
 * <p><b>PENDIENTE (historia de usuario de seguridad/acceso):</b>
 * <ol>
 *   <li>Leer la cabecera {@code X-API-Key} en las rutas públicas.</li>
 *   <li>Validarla contra la tabla de API Keys (BD mysql-gateway; guardar solo el hash).</li>
 *   <li>Aplicar el límite de peticiones por key/IP y responder 429 con {@code Retry-After} si se excede.</li>
 * </ol>
 *
 * <p>Hoy solo deja pasar la petición. Está registrado dentro de la cadena de Spring Security,
 * antes de la validación del JWT.
 */
@Component
public class ApiKeyRateLimitFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
    }
}
