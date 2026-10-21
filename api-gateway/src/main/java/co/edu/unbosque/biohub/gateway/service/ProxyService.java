package co.edu.unbosque.biohub.gateway.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import co.edu.unbosque.biohub.gateway.config.GatewayProperties;
import jakarta.servlet.http.HttpServletRequest;

/** Reenvía la petición al servicio correspondiente según el prefijo /api/&lt;prefijo&gt;/... */
@Service
public class ProxyService {

    /** Cabeceras hop-by-hop, de sesión o que recalcula el cliente HTTP: no se copian. */
    private static final Set<String> CABECERAS_EXCLUIDAS = Set.of(
            "host", "connection", "keep-alive", "transfer-encoding", "te", "trailer", "upgrade",
            "proxy-authorization", "proxy-authenticate", "content-length", "expect",
            "forwarded", "x-real-ip", "origin", "cookie", "set-cookie",
            // CORS y Vary los pone el gateway; si se copian del servicio salen duplicados.
            "vary",
            // La API Key la consume el gateway; no viaja a los servicios.
            "x-api-key");

    /** Prefijos de cabeceras que el cliente no debe poder falsificar hacia los servicios. */
    private static final Set<String> PREFIJOS_EXCLUIDOS = Set.of("x-forwarded-", "x-original-", "access-control-");

    /** Métodos cuyo cuerpo se ignora (no se lee ni se reenvía). */
    /**
     * Secuencias que indican una ruta no normalizada (path traversal, separadores codificados,
     * parámetros de matriz). El firewall de Spring Security ya las bloquea; esto es defensa en
     * profundidad por si alguien relaja el firewall.
     */
    private static final List<String> SECUENCIAS_PROHIBIDAS = List.of(
            "/../", "/./", "//", "%2e", "%2f", "%5c", "\\", ";", "%00", "%25");

    private static final Set<String> METODOS_SIN_CUERPO = Set.of("GET", "HEAD", "OPTIONS", "DELETE", "TRACE");

    private final RestClient restClient;
    private final Map<String, String> rutas;
    private final long tamanoMaximoCuerpo;

    public ProxyService(RestClient proxyRestClient, GatewayProperties props) {
        this.restClient = proxyRestClient;
        this.rutas = props.rutas();
        this.tamanoMaximoCuerpo = props.tamanoMaximoCuerpo().toBytes();
    }

    /** Devuelve la URL base del servicio destino para la ruta, si existe. */
    public Optional<String> resolverDestino(String ruta) {
        if (ruta == null || !ruta.startsWith("/api/")) {
            return Optional.empty();
        }
        String resto = ruta.substring("/api/".length());
        int barra = resto.indexOf('/');
        String prefijo = barra >= 0 ? resto.substring(0, barra) : resto;
        return Optional.ofNullable(rutas.get(prefijo));
    }

    /**
     * Reenvía la petición.
     *
     * @throws CuerpoDemasiadoGrandeException si el cuerpo supera el máximo (→ 413)
     * @throws IllegalArgumentException       si la URI no está normalizada o el query está mal codificado (→ 400)
     * @throws RutaNoEncontradaException     si ningún servicio atiende el prefijo (→ 404)
     */
    public ResponseEntity<byte[]> reenviar(HttpServletRequest request) {
        String ruta = request.getRequestURI();
        if (!rutaNormalizada(ruta)) {
            throw new IllegalArgumentException("Ruta no normalizada");
        }
        Optional<String> destino = resolverDestino(ruta);
        if (destino.isEmpty()) {
            throw new RutaNoEncontradaException();
        }
        URI uri = UriComponentsBuilder.fromUriString(destino.get())
                .path(ruta)
                .query(request.getQueryString())
                .build(true)
                .toUri();

        String metodo = request.getMethod().toUpperCase(Locale.ROOT);
        RestClient.RequestBodySpec peticion = restClient
                .method(HttpMethod.valueOf(metodo))
                .uri(uri)
                .headers(h -> copiarCabeceras(request, h));
        if (!METODOS_SIN_CUERPO.contains(metodo)) {
            byte[] cuerpo = leerCuerpoAcotado(request);
            if (cuerpo.length > 0) {
                peticion.body(cuerpo);
            }
        }
        return peticion.exchange((req, res) -> {
            HttpHeaders cabeceras = new HttpHeaders();
            res.getHeaders().forEach((nombre, valores) -> {
                if (!excluida(nombre)) {
                    cabeceras.addAll(nombre, valores);
                }
            });
            return ResponseEntity.status(res.getStatusCode())
                    .headers(cabeceras)
                    .body(res.getBody().readAllBytes());
        });
    }

    static boolean rutaNormalizada(String ruta) {
        String r = ruta.toLowerCase(Locale.ROOT);
        return !r.endsWith("/..") && !r.endsWith("/.") && SECUENCIAS_PROHIBIDAS.stream().noneMatch(r::contains);
    }

    /** Lee como máximo tamanoMaximoCuerpo bytes; nunca carga en memoria más que eso + 1. */
    private byte[] leerCuerpoAcotado(HttpServletRequest request) {
        if (request.getContentLengthLong() > tamanoMaximoCuerpo) {
            throw new CuerpoDemasiadoGrandeException(tamanoMaximoCuerpo);
        }
        try {
            InputStream entrada = request.getInputStream();
            byte[] datos = entrada.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, tamanoMaximoCuerpo + 1));
            if (datos.length > tamanoMaximoCuerpo) {
                throw new CuerpoDemasiadoGrandeException(tamanoMaximoCuerpo);
            }
            return datos;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static boolean excluida(String nombre) {
        String n = nombre.toLowerCase(Locale.ROOT);
        return CABECERAS_EXCLUIDAS.contains(n) || PREFIJOS_EXCLUIDOS.stream().anyMatch(n::startsWith);
    }

    private void copiarCabeceras(HttpServletRequest request, HttpHeaders destino) {
        for (String nombre : Collections.list(request.getHeaderNames())) {
            if (!excluida(nombre)) {
                destino.addAll(nombre, Collections.list(request.getHeaders(nombre)));
            }
        }
        // Las fija el gateway (no se confía en las que envía el cliente).
        destino.set("X-Forwarded-For", request.getRemoteAddr());
        destino.set("X-Forwarded-Proto", request.getScheme());
    }
}
