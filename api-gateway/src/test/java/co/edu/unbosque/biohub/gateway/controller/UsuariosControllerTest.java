package co.edu.unbosque.biohub.gateway.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.gateway.domain.ClienteIdentidad;
import co.edu.unbosque.biohub.gateway.domain.LimitadorPeticiones;
import co.edu.unbosque.biohub.gateway.domain.RegistroRevocaciones;
import co.edu.unbosque.biohub.gateway.domain.RolInterno;
import co.edu.unbosque.biohub.gateway.domain.UsuarioInterno;
import co.edu.unbosque.biohub.gateway.service.ConflictoException;
import co.edu.unbosque.biohub.gateway.service.IdentidadNoDisponibleException;
import co.edu.unbosque.biohub.gateway.service.ServicioApiKeys;
import co.edu.unbosque.biohub.gateway.service.ServicioUsuarios;
import co.edu.unbosque.biohub.gateway.soporte.SeguridadGatewayEnPrueba;

/** API de usuarios de HU-10, RF17 y RF19. */
@WebMvcTest(UsuariosController.class)
@SeguridadGatewayEnPrueba
class UsuariosControllerTest {

    private static final String CUERPO_VALIDO = """
            {"usuario":"nuevo1","email":"nuevo1@biohub.test","nombre":"Nuevo","apellido":"Uno",
             "rol":"auditor_cientifico"}""";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioUsuarios usuarios;

    @MockitoBean
    private ServicioApiKeys apiKeys;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private LimitadorPeticiones limitador;

    @MockitoBean
    private RegistroRevocaciones revocaciones;

    private static JwtRequestPostProcessor administrador() {
        return jwt().jwt(token -> token.subject("sub-admin"))
                .authorities(new SimpleGrantedAuthority("ROLE_administrador"));
    }

    private static UsuarioInterno usuario(RolInterno rol) {
        return new UsuarioInterno("u1", "nuevo1", "nuevo1@biohub.test", "Nuevo", "Uno", true, List.of(rol));
    }

    @Test
    void crearDevuelveLaContrasenaTemporalUnaVez() throws Exception {
        given(usuarios.crear(any(), eq("sub-admin")))
                .willReturn(new ServicioUsuarios.UsuarioCreado("u1", "temporal-123456789"));

        mvc.perform(post("/api/gateway/usuarios").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content(CUERPO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.id").value("u1"))
                .andExpect(jsonPath("$.contrasenaTemporal").value("temporal-123456789"));
    }

    @Test
    void crearSinRolDa400() throws Exception {
        mvc.perform(post("/api/gateway/usuarios").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"usuario\":\"nuevo1\",\"email\":\"n@b.co\",\"nombre\":\"N\",\"apellido\":\"U\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errores[0].campo").value("rol"));
    }

    @Test
    void rolFueraDelCatalogoDa400() throws Exception {
        mvc.perform(post("/api/gateway/usuarios").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO_VALIDO.replace("auditor_cientifico", "superusuario")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void listarDevuelveElRolConSuNombreDeKeycloak() throws Exception {
        given(usuarios.listar(null, 0, 20)).willReturn(
                new ClienteIdentidad.Pagina(List.of(usuario(RolInterno.CIENTIFICO_ALTO_RANGO)), 1));

        mvc.perform(get("/api/gateway/usuarios").with(administrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].roles[0]").value("cientifico_alto_rango"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void cambiarRolUsaAlAdministradorComoActor() throws Exception {
        given(usuarios.cambiarRol("u1", RolInterno.ANALISTA_AMBIENTAL, "sub-admin"))
                .willReturn(usuario(RolInterno.ANALISTA_AMBIENTAL));

        mvc.perform(put("/api/gateway/usuarios/u1/rol").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"analista_ambiental\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("analista_ambiental"));
    }

    @Test
    void cambiarSuPropioRolDa409() throws Exception {
        given(usuarios.cambiarRol(any(), any(), any())).willThrow(new ConflictoException("No puedes."));

        mvc.perform(put("/api/gateway/usuarios/sub-admin/rol").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"rol\":\"analista_ambiental\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void quitarRolDa204() throws Exception {
        mvc.perform(delete("/api/gateway/usuarios/u1/rol").with(administrador())).andExpect(status().isNoContent());

        verify(usuarios).quitarRol("u1", "sub-admin");
    }

    @Test
    void desactivarUsaElEstadoRecibido() throws Exception {
        given(usuarios.cambiarEstado("u1", false, "sub-admin")).willReturn(usuario(RolInterno.ADMINISTRADOR));

        mvc.perform(put("/api/gateway/usuarios/u1/estado").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"activo\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    void keycloakCaidoDa503SinDetallesInternos() throws Exception {
        given(usuarios.listar(null, 0, 20)).willThrow(new IdentidadNoDisponibleException("Keycloak no responde"));

        mvc.perform(get("/api/gateway/usuarios").with(administrador()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.codigo").value("SERVICIO_NO_DISPONIBLE"))
                .andExpect(jsonPath("$.detail").value("La administración de usuarios no está disponible en este momento."));
    }
}
