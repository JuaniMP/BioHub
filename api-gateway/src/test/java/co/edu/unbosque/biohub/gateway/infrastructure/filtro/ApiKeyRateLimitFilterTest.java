package co.edu.unbosque.biohub.gateway.infrastructure.filtro;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

import co.edu.unbosque.biohub.gateway.controller.ApiExternaController;
import co.edu.unbosque.biohub.gateway.controller.ProxyController;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorAutenticado;
import co.edu.unbosque.biohub.gateway.domain.LimitadorPeticiones;
import co.edu.unbosque.biohub.gateway.domain.RegistroRevocaciones;
import co.edu.unbosque.biohub.gateway.domain.ResultadoLimite;
import co.edu.unbosque.biohub.gateway.service.ProxyService;
import co.edu.unbosque.biohub.gateway.service.ServicioApiKeys;
import co.edu.unbosque.biohub.gateway.soporte.SeguridadGatewayEnPrueba;
import jakarta.servlet.http.HttpServletRequest;

/** Comportamiento del filtro por tipo de ruta: HU-08, HU-12, RNF11, SEC-05, SG-17 a SG-19. */
@WebMvcTest({ApiExternaController.class, ProxyController.class})
@SeguridadGatewayEnPrueba
class ApiKeyRateLimitFilterTest {

    private static final String KEY = "bh_pub_abcd2345_valor-de-prueba";
    private static final String RUTA_EXTERNA = "/api/v1/externa/fichas";
    private static final UUID CONSUMIDOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Duration VENTANA = Duration.ofSeconds(60);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProxyService proxyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ServicioApiKeys apiKeys;

    @MockitoBean
    private LimitadorPeticiones limitador;

    @MockitoBean
    private RegistroRevocaciones revocaciones;

    private static ResultadoLimite permitido(int limite, int restantes) {
        return new ResultadoLimite(true, limite, restantes, Duration.ofSeconds(40));
    }

    private static ResultadoLimite excedido(int limite) {
        return new ResultadoLimite(false, limite, 0, Duration.ofMillis(12_400));
    }

    @BeforeEach
    void preparar() {
        given(limitador.consultar(anyString(), anyInt(), any())).willReturn(permitido(20, 20));
        given(limitador.consumir(anyString(), anyInt(), any())).willReturn(permitido(60, 59));
        given(proxyService.reenviarApiExterna(any(HttpServletRequest.class)))
                .willReturn(ResponseEntity.ok("externa".getBytes()));
        given(proxyService.reenviar(any(HttpServletRequest.class))).willReturn(ResponseEntity.ok("portal".getBytes()));
    }

    private void keyValida(Integer limitePropio) {
        given(apiKeys.validar(KEY)).willReturn(Optional.of(
                new ConsumidorAutenticado(CONSUMIDOR, UUID.randomUUID(), "bh_pub_abcd2345", limitePropio)));
    }

    @Test
    void sinKeyDa401ConProblemaYNoLlegaAlServicio() throws Exception {
        given(apiKeys.validar(isNull())).willReturn(Optional.empty());

        mvc.perform(get(RUTA_EXTERNA))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "ApiKey"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.codigo").value("API_KEY_INVALIDA"))
                .andExpect(jsonPath("$.instance").value(RUTA_EXTERNA));
        verify(proxyService, never()).reenviarApiExterna(any());
        verify(limitador).consumir(eq("fallos:127.0.0.1"), eq(20), eq(VENTANA));
    }

    @Test
    void keyInvalidaDa401() throws Exception {
        given(apiKeys.validar("inventada")).willReturn(Optional.empty());

        mvc.perform(get(RUTA_EXTERNA).header("X-API-Key", "inventada")).andExpect(status().isUnauthorized());
        verify(proxyService, never()).reenviarApiExterna(any());
    }

    @Test
    void keyValidaPasaYTraeCabecerasDeLimite() throws Exception {
        keyValida(null);

        mvc.perform(get(RUTA_EXTERNA + "/3f2a9c1e-5b7d-4e8a-9f10-2c3d4e5f6a7b").header("X-API-Key", KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("X-RateLimit-Limit", "60"))
                .andExpect(header().string("X-RateLimit-Remaining", "59"));
        verify(limitador).consumir(eq("consumidor:" + CONSUMIDOR), eq(60), eq(VENTANA));
    }

    @Test
    void usaElLimitePropioDelConsumidor() throws Exception {
        keyValida(500);

        mvc.perform(get(RUTA_EXTERNA).header("X-API-Key", KEY)).andExpect(status().isOk());
        verify(limitador).consumir(eq("consumidor:" + CONSUMIDOR), eq(500), eq(VENTANA));
    }

    @Test
    void superarElLimiteDa429ConRetryAfterYNoLlegaAlServicio() throws Exception {
        keyValida(null);
        given(limitador.consumir(eq("consumidor:" + CONSUMIDOR), anyInt(), any())).willReturn(excedido(60));

        mvc.perform(get(RUTA_EXTERNA).header("X-API-Key", KEY))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "13"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"))
                .andExpect(jsonPath("$.title").value("Demasiadas peticiones"));
        verify(proxyService, never()).reenviarApiExterna(any());
    }

    @Test
    void demasiadasKeysInvalidasBloqueanSinConsultarLaBase() throws Exception {
        given(limitador.consultar(eq("fallos:127.0.0.1"), anyInt(), any())).willReturn(excedido(20));

        mvc.perform(get(RUTA_EXTERNA).header("X-API-Key", "inventada")).andExpect(status().isTooManyRequests());
        verifyNoInteractions(apiKeys);
    }

    @Test
    void siLaBaseNoRespondeRechazaCon503YNoDejaPasar() throws Exception {
        given(apiKeys.validar(KEY)).willThrow(new DataAccessResourceFailureException("caída"));

        mvc.perform(get(RUTA_EXTERNA).header("X-API-Key", KEY))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"));
        verify(proxyService, never()).reenviarApiExterna(any());
    }

    @Test
    void siNoHayConexionALaBaseTambienRechaza() throws Exception {
        given(apiKeys.validar(KEY)).willThrow(new CannotCreateTransactionException("sin conexión"));

        mvc.perform(get(RUTA_EXTERNA).header("X-API-Key", KEY)).andExpect(status().isServiceUnavailable());
    }

    @Test
    void unJwtValidoNoReemplazaLaApiKey() throws Exception {
        given(apiKeys.validar(isNull())).willReturn(Optional.empty());
        given(jwtDecoder.decode(anyString())).willReturn(Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("u").claim("realm_access", Map.of("roles", List.of("administrador")))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());

        mvc.perform(get(RUTA_EXTERNA).header("Authorization", "Bearer t")).andExpect(status().isUnauthorized());
        verify(proxyService, never()).reenviarApiExterna(any());
    }

    @Test
    void laApiExternaEsSoloLectura() throws Exception {
        keyValida(null);

        mvc.perform(post(RUTA_EXTERNA).header("X-API-Key", KEY)).andExpect(status().isUnauthorized());
        verifyNoInteractions(apiKeys);
    }

    @Test
    void otrosRecursosDeLaApiExternaNoExisten() throws Exception {
        keyValida(null);

        mvc.perform(get("/api/v1/externa/eventos/recibidos").header("X-API-Key", KEY))
                .andExpect(status().isUnauthorized());
        verify(proxyService, never()).reenviar(any());
        verify(proxyService, never()).reenviarApiExterna(any());
    }

    @Test
    void elPortalNoPideKeyYCuentaPorIp() throws Exception {
        mvc.perform(get("/api/publica/fichas")).andExpect(status().isOk());

        verify(limitador).consumir(eq("ip:127.0.0.1"), eq(120), eq(VENTANA));
        verifyNoInteractions(apiKeys);
    }

    @Test
    void elPortalDa429CuandoLaIpSuperaElLimite() throws Exception {
        given(limitador.consumir(eq("ip:127.0.0.1"), anyInt(), any())).willReturn(excedido(120));

        mvc.perform(get("/api/publica/fichas"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "13"))
                .andExpect(jsonPath("$.codigo").value("LIMITE_EXCEDIDO"))
                .andExpect(jsonPath("$.detail", containsString("Superaste el límite")));
        verify(proxyService, never()).reenviar(any());
    }

    @Test
    void xForwardedForFalsificadoNoEvitaElLimite() throws Exception {
        mvc.perform(get("/api/publica/fichas").header("X-Forwarded-For", "6.6.6.6")).andExpect(status().isOk());

        verify(limitador).consumir(eq("ip:127.0.0.1"), anyInt(), any());
    }

    @Test
    void lasRutasConJwtNoPasanPorElLimitador() throws Exception {
        given(jwtDecoder.decode(anyString())).willReturn(Jwt.withTokenValue("t").header("alg", "RS256")
                .subject("u").claim("realm_access", Map.of("roles", List.of("cientifico_alto_rango")))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build());

        mvc.perform(get("/api/cientifico/ubicacion/mapa").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
        verifyNoInteractions(limitador);
    }

    @Test
    void elPreflightDeCorsNoGastaCupo() throws Exception {
        mvc.perform(options("/api/publica/fichas")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk());
        verifyNoInteractions(limitador);
    }
}
