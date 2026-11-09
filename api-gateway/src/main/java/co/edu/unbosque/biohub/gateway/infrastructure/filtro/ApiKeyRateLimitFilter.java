package co.edu.unbosque.biohub.gateway.infrastructure.filtro;

import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.web.filter.OncePerRequestFilter;

import co.edu.unbosque.biohub.gateway.config.PropiedadesLimites;
import co.edu.unbosque.biohub.gateway.config.RutasGateway;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorAutenticado;
import co.edu.unbosque.biohub.gateway.domain.LimitadorPeticiones;
import co.edu.unbosque.biohub.gateway.domain.ResultadoLimite;
import co.edu.unbosque.biohub.gateway.service.ServicioApiKeys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Control de entrada de lo que no usa JWT (HU-08, HU-12, RNF11, ASR8):
 * <ul>
 *   <li>{@code /api/v1/externa/fichas/**}: exige API Key y cuenta por consumidor.</li>
 *   <li>{@code GET /api/publica/**}: portal sin login (D16), cuenta por IP.</li>
 *   <li>Lo demás sigue sin cambios hacia la validación del JWT.</li>
 * </ul>
 *
 * <p>Falla cerrado (SEC-05): este filtro no autoriza nada, solo marca la petición con
 * {@code ROLE_API_CLIENT}. Si no llega a marcarla, SecurityConfig la rechaza. La IP sale de
 * {@code getRemoteAddr()}, nunca de {@code X-Forwarded-For}, que el cliente puede inventar.
 */
@Component
public class ApiKeyRateLimitFilter extends OncePerRequestFilter {

    public static final String CABECERA_API_KEY = "X-API-Key";

    private static final Logger log = LoggerFactory.getLogger(ApiKeyRateLimitFilter.class);

    private final RutasGateway rutas;
    private final ServicioApiKeys apiKeys;
    private final LimitadorPeticiones limitador;
    private final PropiedadesLimites limites;
    private final EscritorProblema escritor;

    public ApiKeyRateLimitFilter(RutasGateway rutas, ServicioApiKeys apiKeys, LimitadorPeticiones limitador,
                                 PropiedadesLimites limites, EscritorProblema escritor) {
        this.rutas = rutas;
        this.apiKeys = apiKeys;
        this.limitador = limitador;
        this.limites = limites;
        this.escritor = escritor;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        if (rutas.esApiExterna(peticion)) {
            atenderApiExterna(peticion, respuesta, cadena);
        } else if (rutas.esPortal(peticion)) {
            atenderPortal(peticion, respuesta, cadena);
        } else {
            cadena.doFilter(peticion, respuesta);
        }
    }

    private void atenderPortal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        ResultadoLimite resultado = limitador.consumir("ip:" + peticion.getRemoteAddr(), limites.porIpPortal(),
                limites.ventana());
        if (!resultado.permitido()) {
            escritor.limiteExcedido(peticion, respuesta, resultado);
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    private void atenderApiExterna(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        // Se revisa antes de validar: quien prueba keys inventadas no debe llegar a consultar la BD.
        String claveFallos = "fallos:" + peticion.getRemoteAddr();
        ResultadoLimite bloqueo = limitador.consultar(claveFallos, limites.fallosKeyPorIp(), limites.ventana());
        if (!bloqueo.permitido()) {
            escritor.limiteExcedido(peticion, respuesta, bloqueo);
            return;
        }

        Optional<ConsumidorAutenticado> consumidor;
        try {
            consumidor = apiKeys.validar(peticion.getHeader(CABECERA_API_KEY));
        } catch (DataAccessException | TransactionException e) {
            // Sin BD no se puede saber si la key es válida: se rechaza, nunca se deja pasar.
            log.warn("No se pudo validar la API Key: base de datos no disponible ({})", e.getClass().getSimpleName());
            escritor.servicioNoDisponible(peticion, respuesta);
            return;
        }
        if (consumidor.isEmpty()) {
            limitador.consumir(claveFallos, limites.fallosKeyPorIp(), limites.ventana());
            escritor.apiKeyInvalida(peticion, respuesta);
            return;
        }

        ConsumidorAutenticado autenticado = consumidor.get();
        int limite = autenticado.limitePorMinuto() != null ? autenticado.limitePorMinuto() : limites.porConsumidor();
        ResultadoLimite resultado = limitador.consumir("consumidor:" + autenticado.consumidorId(), limite,
                limites.ventana());
        respuesta.setHeader("X-RateLimit-Limit", String.valueOf(resultado.limite()));
        respuesta.setHeader("X-RateLimit-Remaining", String.valueOf(resultado.restantes()));
        if (!resultado.permitido()) {
            escritor.limiteExcedido(peticion, respuesta, resultado);
            return;
        }

        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new ApiKeyAuthenticationToken(autenticado));
        SecurityContextHolder.setContext(contexto);
        cadena.doFilter(peticion, respuesta);
    }
}
