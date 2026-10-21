package co.edu.unbosque.biohub.cientifico.controller;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.cientifico.config.SecurityConfig;

@WebMvcTest(PingController.class)
@Import(SecurityConfig.class)
class PingControllerTest {

    @Autowired
    private MockMvc mvc;

    /** Sustituye a Keycloak: el token "falso" se decodifica a este JWT. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void pingConJwtDevuelveServicioUsuarioYRolesDeNegocio() throws Exception {
        Jwt jwt = Jwt.withTokenValue("token-prueba")
                .header("alg", "RS256")
                .subject("6f1c-uuid")
                .claim("preferred_username", "investigador1")
                .claim("realm_access", Map.of("roles",
                        List.of("investigador_campo", "offline_access", "default-roles-biohub")))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        given(jwtDecoder.decode(anyString())).willReturn(jwt);

        mvc.perform(get("/api/cientifico/ping").header("Authorization", "Bearer token-prueba"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicio").value("servicio-cientifico"))
                .andExpect(jsonPath("$.usuario").value("investigador1"))
                .andExpect(jsonPath("$.roles.length()").value(1))
                .andExpect(jsonPath("$.roles[0]").value("investigador_campo"));
    }

    @Test
    void pingSinToken() throws Exception {
        mvc.perform(get("/api/cientifico/ping"))
                .andExpect(status().isUnauthorized());
    }
}
