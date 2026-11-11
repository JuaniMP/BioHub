package co.edu.unbosque.biohub.gateway.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.gateway.domain.LimitadorPeticiones;
import co.edu.unbosque.biohub.gateway.domain.RegistroRevocaciones;
import co.edu.unbosque.biohub.gateway.domain.ResultadoLimite;
import co.edu.unbosque.biohub.gateway.domain.RolInterno;
import co.edu.unbosque.biohub.gateway.service.ProxyService;
import co.edu.unbosque.biohub.gateway.service.ServicioApiKeys;
import co.edu.unbosque.biohub.gateway.service.ServicioConsumidores;
import co.edu.unbosque.biohub.gateway.config.PropiedadesLimites;
import co.edu.unbosque.biohub.gateway.service.ServicioUsuarios;
import co.edu.unbosque.biohub.gateway.soporte.SeguridadGatewayEnPrueba;
import jakarta.servlet.http.HttpServletRequest;

/** SEC-02: denegación por defecto y control grueso por rol en el gateway (HU-10 c.2). */
@WebMvcTest({ProxyController.class, PingController.class, ConsumidoresController.class, UsuariosController.class})
@SeguridadGatewayEnPrueba
class MatrizAccesoGatewayTest {

    private static final String TOKEN = "Bearer t";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProxyService proxyService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ServicioApiKeys apiKeys;

    @MockitoBean
    private ServicioConsumidores consumidores;

    @MockitoBean
    private ServicioUsuarios usuarios;

    @MockitoBean
    private LimitadorPeticiones limitador;

    @MockitoBean
    private RegistroRevocaciones revocaciones;

    @Autowired
    private PropiedadesLimites limites;

    @BeforeEach
    void preparar() {
        given(limitador.consumir(anyString(), anyInt(), any()))
                .willReturn(new ResultadoLimite(true, 120, 119, Duration.ofSeconds(60)));
        given(proxyService.reenviar(any(HttpServletRequest.class))).willReturn(ResponseEntity.ok("ok".getBytes()));
    }

    private void tokenCon(String... roles) {
        given(jwtDecoder.decode(anyString())).willReturn(Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("usuario-1")
                .claim("preferred_username", "alguien")
                .claim("realm_access", Map.of("roles", List.of(roles)))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build());
    }

    @ParameterizedTest
    @EnumSource(RolInterno.class)
    void cadaRolInternoEntraALosServiciosInternos(RolInterno rol) throws Exception {
        tokenCon(rol.nombre());

        for (String ruta : List.of("/api/cientifico/ping", "/api/equipos/ping", "/api/reportes/ping", "/api/gateway/ping")) {
            mvc.perform(get(ruta).header("Authorization", TOKEN)).andExpect(status().isOk());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/cientifico/x", "/api/equipos/x", "/api/reportes/x", "/api/gateway/ping"})
    void usuarioSinRolRecibe403ConCodigoYNoSeReenvia(String ruta) throws Exception {
        tokenCon("offline_access", "default-roles-biohub");

        mvc.perform(get(ruta).header("Authorization", TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ROL_INSUFICIENTE"));
        verify(proxyService, never()).reenviar(any());
    }

    @Test
    void usuarioSinRolSiEntraAlPortalPublico() throws Exception {
        tokenCon("offline_access");

        mvc.perform(get("/api/publica/fichas").header("Authorization", TOKEN)).andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/gateway/consumidores", "/api/gateway/usuarios"})
    void administracionSinTokenDa401(String ruta) throws Exception {
        mvc.perform(get(ruta)).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @EnumSource(value = RolInterno.class, names = "ADMINISTRADOR", mode = EnumSource.Mode.EXCLUDE)
    void soloElAdministradorAdministra(RolInterno rol) throws Exception {
        tokenCon(rol.nombre());

        for (String ruta : List.of("/api/gateway/consumidores", "/api/gateway/usuarios")) {
            mvc.perform(get(ruta).header("Authorization", TOKEN)).andExpect(status().isForbidden());
        }
    }

    @Test
    void rutaDesconocidaSeDeniegaPorDefecto() throws Exception {
        tokenCon("administrador");

        mvc.perform(get("/api/desconocido/x")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/desconocido/x").header("Authorization", TOKEN)).andExpect(status().isForbidden());
        mvc.perform(get("/otra/cosa").header("Authorization", TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    void actuatorSoloExponeHealth() throws Exception {
        tokenCon("administrador");

        mvc.perform(get("/actuator/env").header("Authorization", TOKEN)).andExpect(status().isForbidden());
    }

    @Test
    void metodosDeEscrituraEnElPortalSeDeniegan() throws Exception {
        tokenCon("administrador");

        mvc.perform(post("/api/publica/fichas")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/publica/fichas").header("Authorization", TOKEN)).andExpect(status().isForbidden());
        verify(proxyService, never()).reenviar(any());
    }

    @Test
    void tokenEmitidoAntesDeUnCambioDeRolSeRechazaAunqueSigaVigente() throws Exception {
        tokenCon("cientifico_alto_rango");
        given(revocaciones.revocado(eq("usuario-1"), any())).willReturn(true);

        mvc.perform(get("/api/cientifico/ubicacion/mapa").header("Authorization", TOKEN))
                .andExpect(status().isUnauthorized());
        verify(proxyService, never()).reenviar(any());
    }
}
