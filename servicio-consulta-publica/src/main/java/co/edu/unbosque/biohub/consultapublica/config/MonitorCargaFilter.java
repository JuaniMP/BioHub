package co.edu.unbosque.biohub.consultapublica.config;

import java.io.IOException;

import org.springframework.web.filter.OncePerRequestFilter;

import co.edu.unbosque.biohub.consultapublica.service.MonitorCarga;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Alimenta al {@link MonitorCarga} con cada lectura pública ({@code /api/publica/fichas...}): cuenta las
 * peticiones en curso y mide su duración total (incluida la seguridad y la serialización). No toca la respuesta.
 */
public class MonitorCargaFilter extends OncePerRequestFilter {

    static final String PREFIJO = "/api/publica/fichas";

    private final MonitorCarga monitor;

    public MonitorCargaFilter(MonitorCarga monitor) {
        this.monitor = monitor;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(PREFIJO);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long inicio = System.nanoTime();
        monitor.iniciarPeticion();
        try {
            chain.doFilter(request, response);
        } finally {
            monitor.terminarPeticion((System.nanoTime() - inicio) / 1_000_000);
        }
    }
}
