package co.edu.unbosque.biohub.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.gateway.config.GatewayProperties;
import co.edu.unbosque.biohub.gateway.config.SecurityConfig;
import co.edu.unbosque.biohub.gateway.infrastructure.filtro.ApiKeyRateLimitFilter;
import co.edu.unbosque.biohub.gateway.service.ProxyService;
import jakarta.servlet.http.HttpServletRequest;

@WebMvcTest({ProxyController.class, PingController.class})
@Import({SecurityConfig.class, ApiKeyRateLimitFilter.class})
@EnableConfigurationProperties(GatewayProperties.class)
@TestPropertySource(properties = {
        "biohub.gateway.rutas-publicas=publica",
        "biohub.gateway.origenes-permitidos=http://localhost:5173"
})
class GatewaySecurityTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProxyService proxyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    private void tokenValidoPara(String usuario, String... roles) {
        given(jwtDecoder.decode(anyString())).willReturn(Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("uuid")
                .claim("preferred_username", usuario)
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    @Test
    void rutaProtegidaSinTokenDa401YNoSeReenvia() throws Exception {
        mvc.perform(get("/api/cientifico/ping")).andExpect(status().isUnauthorized());
        verify(proxyService, never()).reenviar(any());
    }

    @Test
    void rutaProtegidaConTokenSeReenvia() throws Exception {
        tokenValidoPara("auditor1", "auditor_cientifico");
        given(proxyService.reenviar(any(HttpServletRequest.class))).willReturn(ResponseEntity.ok("ok".getBytes()));

        mvc.perform(get("/api/equipos/ping").header("Authorization", "Bearer t"))
                .andExpect(status().isOk());
    }

    @Test
    void rutaPublicaSinTokenSeReenvia() throws Exception {
        given(proxyService.reenviar(any(HttpServletRequest.class))).willReturn(ResponseEntity.ok("ok".getBytes()));

        mvc.perform(get("/api/publica/ping")).andExpect(status().isOk());
    }

    @Test
    void pingDelGatewayDevuelveUsuarioYRoles() throws Exception {
        tokenValidoPara("admin1", "administrador", "offline_access");

        mvc.perform(get("/api/gateway/ping").header("Authorization", "Bearer t"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicio").value("api-gateway"))
                .andExpect(jsonPath("$.usuario").value("admin1"))
                .andExpect(jsonPath("$.roles").value(org.hamcrest.Matchers.contains("administrador")));
    }

    @Test
    void corsPermiteSoloOrigenesConfigurados() throws Exception {
        mvc.perform(options("/api/cientifico/ping")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mvc.perform(options("/api/cientifico/ping")
                        .header("Origin", "http://malicioso.com")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }
}
