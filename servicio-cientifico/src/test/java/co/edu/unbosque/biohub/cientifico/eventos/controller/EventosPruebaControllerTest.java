package co.edu.unbosque.biohub.cientifico.eventos.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.cientifico.config.SecurityConfig;
import co.edu.unbosque.biohub.cientifico.eventos.domain.EventoSobre;
import co.edu.unbosque.biohub.cientifico.eventos.service.EventoNoPublicadoException;
import co.edu.unbosque.biohub.cientifico.eventos.service.PublicadorEventos;

@WebMvcTest(EventosPruebaController.class)
@Import(SecurityConfig.class)
class EventosPruebaControllerTest {

    private static final String URL = "/api/cientifico/eventos/prueba";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private PublicadorEventos publicador;

    private static EventoSobre evento(String mensaje) {
        return new EventoSobre(UUID.randomUUID(), "prueba.ping", "servicio-cientifico", Instant.now(), "admin1",
                Map.of("mensaje", mensaje));
    }

    /** Authentication.getName() = preferred_username, como hace el KeycloakJwtAuthenticationConverter. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor admin() {
        return jwt().jwt(j -> j.claim("preferred_username", "admin1"))
                .authorities(new SimpleGrantedAuthority("ROLE_administrador"));
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(post(URL)).andExpect(status().isUnauthorized());
        verify(publicador, never()).publicar(any(), any(), any());
    }

    @Test
    void sinRolAdministradorDevuelve403() throws Exception {
        mvc.perform(post(URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_investigador_campo"))))
                .andExpect(status().isForbidden());
        verify(publicador, never()).publicar(any(), any(), any());
    }

    @Test
    void administradorPublicaYRecibe202ConElSobre() throws Exception {
        given(publicador.publicar(eq("prueba.ping"), any(), eq(Map.of("mensaje", "hola"))))
                .willReturn(evento("hola"));

        mvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"hola\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.tipo").value("prueba.ping"))
                .andExpect(jsonPath("$.origen").value("servicio-cientifico"))
                .andExpect(jsonPath("$.datos.mensaje").value("hola"))
                .andExpect(jsonPath("$.id").exists());
    }

    @Test
    void cuerpoOpcionalUsaMensajePorDefecto() throws Exception {
        given(publicador.publicar(eq("prueba.ping"), any(), eq(Map.of("mensaje", "ping"))))
                .willReturn(evento("ping"));

        mvc.perform(post(URL).with(admin())).andExpect(status().isAccepted());
        mvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void mensajeDeMasDe200CaracteresDevuelve400() throws Exception {
        mvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"" + "a".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(publicador, never()).publicar(any(), any(), any());
    }

    @Test
    void mensajeDeExactamente200CaracteresEsValido() throws Exception {
        given(publicador.publicar(any(), any(), any())).willReturn(evento("a"));
        mvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensaje\":\"" + "a".repeat(200) + "\"}"))
                .andExpect(status().isAccepted());
    }

    @Test
    void jsonRotoDevuelve400() throws Exception {
        mvc.perform(post(URL).with(admin()).contentType(MediaType.APPLICATION_JSON).content("{roto"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void brokerCaidoDevuelve503SinDetalles() throws Exception {
        given(publicador.publicar(any(), any(), any()))
                .willThrow(new EventoNoPublicadoException(new RuntimeException("amqp://10.0.0.5:5672 refused")));

        mvc.perform(post(URL).with(admin()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("10.0.0.5"))));
    }
}
