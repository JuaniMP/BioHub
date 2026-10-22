package co.edu.unbosque.biohub.reportes.eventos.controller;

import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.reportes.config.SecurityConfig;
import co.edu.unbosque.biohub.reportes.eventos.domain.EventoSobre;
import co.edu.unbosque.biohub.reportes.eventos.service.AlmacenEventosRecibidos;

@WebMvcTest(EventosRecibidosController.class)
@Import(SecurityConfig.class)
class EventosRecibidosControllerTest {

    private static final String URL = "/api/reportes/eventos/recibidos";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AlmacenEventosRecibidos almacen;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
    }

    @Test
    void sinRolAdministradorDevuelve403() throws Exception {
        mvc.perform(get(URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_investigador_campo"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void administradorVeLosEventos() throws Exception {
        given(almacen.ultimos()).willReturn(List.of(new EventoSobre(UUID.randomUUID(), "prueba.ping",
                "servicio-cientifico", Instant.parse("2026-10-21T12:00:00Z"), "admin1", Map.of("mensaje", "hola"))));

        mvc.perform(get(URL).with(jwt().authorities(new SimpleGrantedAuthority("ROLE_administrador"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.eventos[0].tipo").value("prueba.ping"))
                .andExpect(jsonPath("$.eventos[0].datos.mensaje").value("hola"));
    }
}
