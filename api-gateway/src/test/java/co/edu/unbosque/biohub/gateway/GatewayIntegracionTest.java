package co.edu.unbosque.biohub.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.gateway.domain.AlmacenCredenciales;
import co.edu.unbosque.biohub.gateway.domain.CredencialApiKey;
import co.edu.unbosque.biohub.gateway.domain.RegistroRevocaciones;
import co.edu.unbosque.biohub.gateway.service.RegistroUltimoUso;
import co.edu.unbosque.biohub.gateway.soporte.ContenedoresGateway;
import co.edu.unbosque.biohub.gateway.soporte.ServicioFalso;

/**
 * El gateway completo sobre HTTP real, con MySQL y Redis reales en contenedores desechables. Comprueba
 * también la migración con las reglas de Aiven. D30: nunca se conecta a Aiven.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "biohub.gateway.limites.por-ip-portal=5",
        "biohub.gateway.limites.por-consumidor=60",
        "biohub.gateway.limites.fallos-key-por-ip=3",
        "biohub.gateway.limites.ventana=60s",
        "biohub.gateway.identidad.client-secret="
})
@ActiveProfiles("test")
class GatewayIntegracionTest {

    private static final String ADMIN = "Bearer admin";
    private static final String CABECERA_KEY = "X-API-Key";

    @Container
    static final MySQLContainer<?> MYSQL = ContenedoresGateway.mysqlComoAiven();

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    private static final ServicioFalso SERVICIO = crearServicio();

    private static ServicioFalso crearServicio() {
        try {
            return new ServicioFalso();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registro.add("spring.datasource.username", MYSQL::getUsername);
        registro.add("spring.datasource.password", MYSQL::getPassword);
        registro.add("spring.flyway.user", MYSQL::getUsername);
        registro.add("spring.flyway.password", MYSQL::getPassword);
        registro.add("spring.data.redis.host", REDIS::getHost);
        registro.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        registro.add("spring.data.redis.password", () -> "");
        registro.add("biohub.gateway.rutas.cientifico", SERVICIO::url);
        registro.add("biohub.gateway.rutas.publica", SERVICIO::url);
    }

    @AfterAll
    static void apagar() {
        SERVICIO.close();
    }

    @LocalServerPort
    private int puerto;

    @Autowired
    private StringRedisTemplate redis;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private AlmacenCredenciales credenciales;

    @Autowired
    private RegistroRevocaciones revocaciones;

    @Autowired
    private RegistroUltimoUso ultimoUso;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private final HttpClient cliente = HttpClient.newHttpClient();

    @BeforeEach
    void limpiar() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        SERVICIO.limpiar();
        given(jwtDecoder.decode(anyString())).willAnswer(llamada -> {
            String token = llamada.getArgument(0);
            String rol = token.equals("admin") ? "administrador" : "investigador_campo";
            return Jwt.withTokenValue(token).header("alg", "RS256").subject("sub-" + token)
                    .claim("realm_access", Map.of("roles", List.of(rol)))
                    .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        });
    }

    private URI uri(String ruta) {
        return URI.create("http://127.0.0.1:" + puerto + ruta);
    }

    private HttpResponse<String> obtener(String ruta, String... cabeceras) throws Exception {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(uri(ruta)).GET();
        if (cabeceras.length > 0) {
            peticion.headers(cabeceras);
        }
        return cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> enviar(String metodo, String ruta, String cuerpo) throws Exception {
        HttpRequest peticion = HttpRequest.newBuilder(uri(ruta))
                .method(metodo, cuerpo == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(cuerpo))
                .header("Authorization", ADMIN).header("Content-Type", "application/json").build();
        return cliente.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode cuerpo(HttpResponse<String> respuesta) throws Exception {
        return json.readTree(respuesta.body());
    }

    private String crearConsumidor(Integer limite) throws Exception {
        String cuerpo = "{\"nombre\":\"Consumidor " + UUID.randomUUID() + "\",\"contactoEmail\":\"datos@unal.edu.co\""
                + (limite == null ? "" : ",\"limitePorMinuto\":" + limite) + "}";
        HttpResponse<String> creado = enviar("POST", "/api/gateway/consumidores", cuerpo);
        assertThat(creado.statusCode()).isEqualTo(201);
        return cuerpo(creado).get("id").asText();
    }

    private JsonNode emitirKey(String consumidorId) throws Exception {
        HttpResponse<String> emitida = enviar("POST", "/api/gateway/consumidores/" + consumidorId + "/keys", null);
        assertThat(emitida.statusCode()).isEqualTo(201);
        return cuerpo(emitida);
    }

    @Test
    void cicloCompletoEmitirUsarYRevocarDeInmediato() throws Exception {
        String consumidor = crearConsumidor(null);
        JsonNode key = emitirKey(consumidor);
        String valor = key.get("apiKey").asText();

        HttpResponse<String> conKey = obtener("/api/v1/externa/fichas?q=rana", CABECERA_KEY, valor);
        assertThat(conKey.statusCode()).isEqualTo(200);
        assertThat(conKey.headers().firstValue("X-RateLimit-Limit")).contains("60");
        assertThat(conKey.headers().firstValue("Cache-Control").orElseThrow()).contains("private");
        assertThat(SERVICIO.recibidas()).extracting(ServicioFalso.Recibida::ruta).containsExactly("/api/publica/fichas?q=rana");

        // La segunda petición ya sale de la caché: la revocación debe ganarle igual.
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, valor).statusCode()).isEqualTo(200);
        assertThat(enviar("DELETE", "/api/gateway/keys/" + key.get("id").asText(), null).statusCode()).isEqualTo(204);

        HttpResponse<String> revocada = obtener("/api/v1/externa/fichas", CABECERA_KEY, valor);
        assertThat(revocada.statusCode()).isEqualTo(401);
        assertThat(cuerpo(revocada).get("codigo").asText()).isEqualTo("API_KEY_INVALIDA");
        assertThat(SERVICIO.recibidas()).hasSize(2);
    }

    @Test
    void laBaseGuardaSoloElHashDeLaKey() throws Exception {
        JsonNode key = emitirKey(crearConsumidor(null));
        String valor = key.get("apiKey").asText();

        Map<String, Object> fila = jdbc.queryForMap(
                "SELECT prefijo, HEX(hash_sha256) AS hash, estado FROM api_key WHERE prefijo = ?",
                key.get("prefijo").asText());

        assertThat(fila.get("estado")).isEqualTo("ACTIVA");
        assertThat(fila.toString()).doesNotContain(valor).doesNotContain(valor.substring(16));
        assertThat((String) fila.get("hash")).hasSize(64);
    }

    @Test
    void elUltimoUsoSeGuardaEnLaTareaPeriodica() throws Exception {
        String consumidor = crearConsumidor(null);
        String valor = emitirKey(consumidor).get("apiKey").asText();
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, valor).statusCode()).isEqualTo(200);

        ultimoUso.guardarPendientes();

        JsonNode keys = cuerpo(enviar("GET", "/api/gateway/consumidores/" + consumidor + "/keys", null));
        assertThat(keys.get(0).get("ultimoUsoEn").isNull()).isFalse();
    }

    @Test
    void laRespuestaDeListarNoExponeLaKey() throws Exception {
        String consumidor = crearConsumidor(null);
        String valor = emitirKey(consumidor).get("apiKey").asText();

        HttpResponse<String> lista = enviar("GET", "/api/gateway/consumidores/" + consumidor + "/keys", null);

        assertThat(lista.statusCode()).isEqualTo(200);
        assertThat(lista.body()).doesNotContain(valor).doesNotContain("hash");
    }

    @Test
    void superarElLimiteDelConsumidorDa429YCambiarloSeAplicaDeInmediato() throws Exception {
        String consumidor = crearConsumidor(2);
        String valor = emitirKey(consumidor).get("apiKey").asText();

        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, valor).statusCode()).isEqualTo(200);
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, valor).statusCode()).isEqualTo(200);

        HttpResponse<String> excedida = obtener("/api/v1/externa/fichas", CABECERA_KEY, valor);
        assertThat(excedida.statusCode()).isEqualTo(429);
        assertThat(Long.parseLong(excedida.headers().firstValue("Retry-After").orElseThrow())).isBetween(1L, 60L);
        assertThat(cuerpo(excedida).get("codigo").asText()).isEqualTo("LIMITE_EXCEDIDO");
        assertThat(SERVICIO.recibidas()).hasSize(2);

        enviar("PUT", "/api/gateway/consumidores/" + consumidor + "/limite", "{\"limitePorMinuto\":10}");
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, valor).statusCode()).isEqualTo(200);
    }

    @Test
    void dosKeysDelMismoConsumidorCompartenElCupo() throws Exception {
        String consumidor = crearConsumidor(2);
        String primera = emitirKey(consumidor).get("apiKey").asText();
        String segunda = emitirKey(consumidor).get("apiKey").asText();

        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, primera).statusCode()).isEqualTo(200);
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, segunda).statusCode()).isEqualTo(200);
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, primera).statusCode()).isEqualTo(429);
    }

    @Test
    void conPeticionesSimultaneasPasanExactamenteLasDelLimite() throws Exception {
        String valor = emitirKey(crearConsumidor(30)).get("apiKey").asText();
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            tareas.add(() -> obtener("/api/v1/externa/fichas", CABECERA_KEY, valor).statusCode());
        }

        List<Integer> estados = new ArrayList<>();
        try (ExecutorService hilos = Executors.newFixedThreadPool(20)) {
            for (Future<Integer> resultado : hilos.invokeAll(tareas)) {
                estados.add(resultado.get());
            }
        }

        assertThat(estados).filteredOn(estado -> estado == 200).hasSize(30);
        assertThat(estados).filteredOn(estado -> estado == 429).hasSize(70);
    }

    @Test
    void elPortalNoPideKeyPeroLimitaPorIp() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(obtener("/api/publica/fichas").statusCode()).isEqualTo(200);
        }

        HttpResponse<String> excedida = obtener("/api/publica/fichas");
        assertThat(excedida.statusCode()).isEqualTo(429);
        assertThat(cuerpo(excedida).get("codigo").asText()).isEqualTo("LIMITE_EXCEDIDO");

        assertThat(obtener("/api/publica/fichas", "X-Forwarded-For", "6.6.6.6").statusCode()).isEqualTo(429);
    }

    @Test
    void elPortalYLaApiExternaLleganAlMismoServicio() throws Exception {
        String valor = emitirKey(crearConsumidor(null)).get("apiKey").asText();
        obtener("/api/publica/fichas");
        obtener("/api/v1/externa/fichas", CABECERA_KEY, valor);

        assertThat(SERVICIO.recibidas()).extracting(ServicioFalso.Recibida::ruta)
                .containsExactly("/api/publica/fichas", "/api/publica/fichas");
    }

    @Test
    void lasKeysInventadasSeBloqueanPorIp() throws Exception {
        String inventada = "bh_pub_abcd2345_" + "A".repeat(43);

        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, inventada).statusCode()).isEqualTo(401);
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, inventada).statusCode()).isEqualTo(401);
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, inventada).statusCode()).isEqualTo(401);
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, inventada).statusCode()).isEqualTo(429);
    }

    @Test
    void unaLecturaTardiaNoRevivirUnaKeyRevocada() throws Exception {
        JsonNode key = emitirKey(crearConsumidor(null));
        String prefijo = key.get("prefijo").asText();
        String valor = key.get("apiKey").asText();
        assertThat(obtener("/api/v1/externa/fichas", CABECERA_KEY, valor).statusCode()).isEqualTo(200);
        String guardado = redis.opsForValue().get("biohub:gateway:key:" + prefijo);
        assertThat(guardado).isNotNull().doesNotContain(valor);

        enviar("DELETE", "/api/gateway/keys/" + key.get("id").asText(), null);

        // Simula la petición lenta: leyó la key activa antes de revocar y escribe en la caché después.
        Boolean sobrescribio = redis.opsForValue().setIfAbsent("biohub:gateway:key:" + prefijo, guardado);
        assertThat(sobrescribio).isFalse();
        Optional<CredencialApiKey> credencial = credenciales.porPrefijo(prefijo);
        assertThat(credencial.orElseThrow().utilizableEn(Instant.now())).isFalse();
    }

    @Test
    void unAdjuntoMultipartLlegaCompletoAlServicio() throws Exception {
        String limite = "----biohub";
        String cuerpoMultipart = "--" + limite + "\r\nContent-Disposition: form-data; name=\"foto\"; filename=\"rana.jpg\""
                + "\r\nContent-Type: image/jpeg\r\n\r\ncontenido-de-la-foto\r\n--" + limite + "--\r\n";
        HttpRequest peticion = HttpRequest.newBuilder(uri("/api/cientifico/hallazgos"))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpoMultipart))
                .header("Authorization", "Bearer investigador")
                .header("Content-Type", "multipart/form-data; boundary=" + limite).build();

        HttpResponse<String> respuesta = cliente.send(peticion, HttpResponse.BodyHandlers.ofString());

        assertThat(respuesta.statusCode()).isEqualTo(200);
        ServicioFalso.Recibida recibida = SERVICIO.recibidas().get(0);
        assertThat(new String(recibida.cuerpo(), StandardCharsets.UTF_8)).isEqualTo(cuerpoMultipart);
        assertThat(recibida.contentType()).contains("boundary=" + limite);
    }

    @Test
    void unCambioDeRolInvalidaElTokenDesdeLaSiguientePeticion() throws Exception {
        String token = "Bearer investigador";
        assertThat(obtener("/api/cientifico/hallazgos", "Authorization", token).statusCode()).isEqualTo(200);

        revocaciones.marcar("sub-investigador");

        assertThat(obtener("/api/cientifico/hallazgos", "Authorization", token).statusCode()).isEqualTo(401);
    }

    @Test
    void sinSecretoDeKeycloakLaAdministracionDeUsuariosDa503() throws Exception {
        HttpResponse<String> respuesta = enviar("GET", "/api/gateway/usuarios", null);

        assertThat(respuesta.statusCode()).isEqualTo(503);
        assertThat(cuerpo(respuesta).get("codigo").asText()).isEqualTo("SERVICIO_NO_DISPONIBLE");
    }

    @Test
    void actuatorHealthEsPublicoYEnvNoExiste() throws Exception {
        assertThat(obtener("/actuator/health").statusCode()).isEqualTo(200);
        assertThat(obtener("/actuator/env", "Authorization", ADMIN).statusCode()).isEqualTo(403);
    }
}
