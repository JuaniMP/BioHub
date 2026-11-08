package co.edu.unbosque.biohub.gateway.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import co.edu.unbosque.biohub.gateway.domain.ApiKey;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorExterno;
import co.edu.unbosque.biohub.gateway.domain.LimitadorPeticiones;
import co.edu.unbosque.biohub.gateway.domain.RegistroRevocaciones;
import co.edu.unbosque.biohub.gateway.service.ConflictoException;
import co.edu.unbosque.biohub.gateway.service.RecursoNoEncontradoException;
import co.edu.unbosque.biohub.gateway.service.ServicioApiKeys;
import co.edu.unbosque.biohub.gateway.service.ServicioConsumidores;
import co.edu.unbosque.biohub.gateway.soporte.SeguridadGatewayEnPrueba;

/** API de administración de HU-12 y RNF11: contrato JSON, validaciones y errores. */
@WebMvcTest(ConsumidoresController.class)
@SeguridadGatewayEnPrueba
class ConsumidoresControllerTest {

    private static final UUID ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final Instant AHORA = Instant.parse("2026-11-05T12:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ServicioConsumidores consumidores;

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

    private static ConsumidorExterno consumidor(Integer limite) {
        return new ConsumidorExterno(ID, "Universidad Nacional", "datos@unal.edu.co", limite, "sub-admin", AHORA);
    }

    private static ApiKey key() {
        return new ApiKey(UUID.randomUUID(), ID, "bh_pub_abcd2345", new byte[32], null, "sub-admin", AHORA);
    }

    @Test
    void crearDevuelve201ConElLimiteEfectivo() throws Exception {
        given(consumidores.crear("Universidad Nacional", "datos@unal.edu.co", null, "sub-admin"))
                .willReturn(consumidor(null));

        mvc.perform(post("/api/gateway/consumidores").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Universidad Nacional\",\"contactoEmail\":\"datos@unal.edu.co\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.limitePorMinuto").isEmpty())
                .andExpect(jsonPath("$.limiteEfectivo").value(60));
    }

    @Test
    void datosInvalidosDan400ConErroresPorCampo() throws Exception {
        mvc.perform(post("/api/gateway/consumidores").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"contactoEmail\":\"no-es-correo\",\"limitePorMinuto\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"))
                .andExpect(jsonPath("$.errores.length()").value(3));
    }

    @Test
    void jsonRotoDa400() throws Exception {
        mvc.perform(post("/api/gateway/consumidores").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void nombreRepetidoDa409() throws Exception {
        given(consumidores.crear(any(), any(), any(), any()))
                .willThrow(new ConflictoException("Ya existe un consumidor con ese nombre."));

        mvc.perform(post("/api/gateway/consumidores").with(administrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Repetido\",\"contactoEmail\":\"a@b.co\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLICTO"));
    }

    @Test
    void listarUsaLaPaginacionDelContrato() throws Exception {
        given(consumidores.listar(0, 20)).willReturn(
                new PageImpl<>(List.of(consumidor(100)), PageRequest.of(0, 20), 1));

        mvc.perform(get("/api/gateway/consumidores").with(administrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].limiteEfectivo").value(100))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.totalPaginas").value(1));
    }

    @Test
    void tamanoDePaginaFueraDeRangoDa400() throws Exception {
        mvc.perform(get("/api/gateway/consumidores").param("size", "101").with(administrador()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }

    @Test
    void cambiarLimiteRespondeElNuevoValor() throws Exception {
        given(consumidores.cambiarLimite(ID, 300, "sub-admin")).willReturn(consumidor(300));

        mvc.perform(put("/api/gateway/consumidores/{id}/limite", ID).with(administrador())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"limitePorMinuto\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limiteEfectivo").value(300));
    }

    @Test
    void emitirMuestraLaKeyUnaVezSinGuardarseEnCache() throws Exception {
        given(apiKeys.emitir(eq(ID), isNull(), eq("sub-admin")))
                .willReturn(new ServicioApiKeys.KeyEmitida(key(), "bh_pub_abcd2345_secreto"));

        mvc.perform(post("/api/gateway/consumidores/{id}/keys", ID).with(administrador()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.apiKey").value("bh_pub_abcd2345_secreto"))
                .andExpect(jsonPath("$.prefijo").value("bh_pub_abcd2345"));
    }

    @Test
    void vencimientoEnElPasadoDa400() throws Exception {
        mvc.perform(post("/api/gateway/consumidores/{id}/keys", ID).with(administrador())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiraEn\":\"2020-01-01T00:00:00Z\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void listarKeysNuncaDevuelveElHash() throws Exception {
        given(apiKeys.listar(ID)).willReturn(List.of(key()));

        mvc.perform(get("/api/gateway/consumidores/{id}/keys", ID).with(administrador()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prefijo").value("bh_pub_abcd2345"))
                .andExpect(content().string(not(containsString("hash"))))
                .andExpect(content().string(not(containsString("apiKey"))));
    }

    @Test
    void revocarDa204() throws Exception {
        mvc.perform(delete("/api/gateway/keys/{id}", ID).with(administrador())).andExpect(status().isNoContent());

        verify(apiKeys).revocar(ID, "sub-admin");
    }

    @Test
    void keyInexistenteDa404() throws Exception {
        willThrow(new RecursoNoEncontradoException("La API Key no existe.")).given(apiKeys).revocar(ID, "sub-admin");

        mvc.perform(delete("/api/gateway/keys/{id}", ID).with(administrador()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NO_ENCONTRADO"));
    }

    @Test
    void identificadorMalFormadoDa400() throws Exception {
        mvc.perform(get("/api/gateway/consumidores/no-es-uuid").with(administrador()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
    }
}
