package co.edu.unbosque.biohub.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import co.edu.unbosque.biohub.gateway.config.GatewayProperties;

class ProxyServiceTest {

    private MockRestServiceServer servidor;
    private ProxyService proxy;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        servidor = MockRestServiceServer.bindTo(builder).build();
        GatewayProperties props = new GatewayProperties(
                Map.of("cientifico", "http://cientifico:8081", "publica", "http://publica:8083"),
                List.of("publica"), List.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), DataSize.ofBytes(10));
        proxy = new ProxyService(builder.build(), props);
    }

    @Test
    void resuelveDestinoPorPrefijo() {
        assertThat(proxy.resolverDestino("/api/cientifico/ping")).contains("http://cientifico:8081");
        assertThat(proxy.resolverDestino("/api/publica")).contains("http://publica:8083");
        assertThat(proxy.resolverDestino("/api/desconocido/x")).isEmpty();
        assertThat(proxy.resolverDestino("/api/cientificox/ping")).isEmpty();
        assertThat(proxy.resolverDestino("/otra/ruta")).isEmpty();
    }

    @Test
    void reenviaRutaQueryYAuthorizationYDevuelveLaRespuesta() {
        servidor.expect(requestTo("http://cientifico:8081/api/cientifico/hallazgos?pagina=2"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer abc"))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/cientifico/hallazgos");
        req.setQueryString("pagina=2");
        req.addHeader("Authorization", "Bearer abc");
        req.addHeader("Cookie", "no-se-reenvia=1");

        ResponseEntity<byte[]> res = proxy.reenviar(req);

        servidor.verify();
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(new String(res.getBody())).isEqualTo("{\"ok\":true}");
    }

    @Test
    void propagaElCodigoDeErrorDelServicio() {
        servidor.expect(requestTo("http://cientifico:8081/api/cientifico/ping"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN));

        ResponseEntity<byte[]> res = proxy.reenviar(new MockHttpServletRequest("GET", "/api/cientifico/ping"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void rutaSinServicioLanzaRutaNoEncontrada() {
        assertThatThrownBy(() -> proxy.reenviar(new MockHttpServletRequest("GET", "/api/nada/ping")))
                .isInstanceOf(RutaNoEncontradaException.class);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
            "/api/publica/../cientifico/ping", "/api/publica/%2e%2e/%2e%2e/actuator/health",
            "/api/publica/%2E%2E/x", "/api/publica/a%2fb", "/api/publica//x", "/api/publica/./x",
            "/api/publica/x;jsessionid=1", "/api/publica/%252e%252e/x", "/api/publica/.."})
    void rechazaRutasNoNormalizadasSinReenviar(String ruta) {
        assertThatThrownBy(() -> proxy.reenviar(new MockHttpServletRequest("GET", ruta)))
                .isInstanceOf(IllegalArgumentException.class);
        servidor.verify();
    }

    @Test
    void reenviaElCuerpoDeUnPostDentroDelLimite() {
        servidor.expect(requestTo("http://cientifico:8081/api/cientifico/hallazgos"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string("{\"a\":1}"))
                .andRespond(withStatus(HttpStatus.CREATED));

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/cientifico/hallazgos");
        req.setContent("{\"a\":1}".getBytes());

        assertThat(proxy.reenviar(req).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        servidor.verify();
    }

    @Test
    void rechazaCuerpoMayorAlLimiteSinReenviar() {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/cientifico/hallazgos");
        req.setContent("01234567890".getBytes()); // 11 bytes > 10

        assertThatThrownBy(() -> proxy.reenviar(req)).isInstanceOf(CuerpoDemasiadoGrandeException.class);
        servidor.verify(); // no hubo ninguna petición al servicio
    }

    @Test
    void ignoraElCuerpoEnGet() {
        servidor.expect(requestTo("http://publica:8083/api/publica/ping"))
                .andExpect(content().string(""))
                .andRespond(withSuccess());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/publica/ping");
        req.setContent("cuerpo-grande-ignorado".getBytes()); // > 10 bytes, pero GET no se lee

        assertThat(proxy.reenviar(req).getStatusCode()).isEqualTo(HttpStatus.OK);
        servidor.verify();
    }

    @Test
    void noReenviaCabecerasFalsificablesNiLaApiKey() {
        servidor.expect(requestTo("http://publica:8083/api/publica/ping"))
                .andExpect(headerDoesNotExist("X-Forwarded-Host"))
                .andExpect(headerDoesNotExist("X-Real-IP"))
                .andExpect(headerDoesNotExist("X-API-Key"))
                .andExpect(header("X-Forwarded-For", "10.0.0.5"))
                .andRespond(withSuccess());

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/publica/ping");
        req.setRemoteAddr("10.0.0.5");
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        req.addHeader("X-Forwarded-Host", "evil.com");
        req.addHeader("X-Real-IP", "1.2.3.4");
        req.addHeader("X-API-Key", "secreta");

        proxy.reenviar(req);
        servidor.verify();
    }

    @Test
    void queryMalCodificadaLanzaIllegalArgument() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/cientifico/ping");
        req.setQueryString("a=%zz");

        assertThatThrownBy(() -> proxy.reenviar(req)).isInstanceOf(IllegalArgumentException.class);
    }
}
