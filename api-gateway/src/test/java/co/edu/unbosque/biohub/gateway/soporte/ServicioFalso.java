package co.edu.unbosque.biohub.gateway.soporte;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Servicio destino real (HTTP de verdad) para comprobar qué llega a través del proxy. */
public final class ServicioFalso implements AutoCloseable {

    public record Recibida(String metodo, String ruta, String contentType, byte[] cuerpo) {
    }

    private final HttpServer servidor;
    private final List<Recibida> recibidas = new CopyOnWriteArrayList<>();

    public ServicioFalso() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::atender);
        servidor.setExecutor(java.util.concurrent.Executors.newFixedThreadPool(16));
        servidor.start();
    }

    private void atender(HttpExchange intercambio) throws IOException {
        byte[] cuerpo = intercambio.getRequestBody().readAllBytes();
        recibidas.add(new Recibida(intercambio.getRequestMethod(), intercambio.getRequestURI().toString(),
                intercambio.getRequestHeaders().getFirst("Content-Type"), cuerpo));
        byte[] respuesta = "{\"content\":[],\"total\":0}".getBytes(StandardCharsets.UTF_8);
        intercambio.getResponseHeaders().add("Content-Type", "application/json");
        intercambio.getResponseHeaders().add("Cache-Control", "public, max-age=30");
        intercambio.sendResponseHeaders(200, respuesta.length);
        intercambio.getResponseBody().write(respuesta);
        intercambio.close();
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public List<Recibida> recibidas() {
        return recibidas;
    }

    public void limpiar() {
        recibidas.clear();
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
