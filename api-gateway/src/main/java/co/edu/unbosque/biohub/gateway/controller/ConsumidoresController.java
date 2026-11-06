package co.edu.unbosque.biohub.gateway.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.gateway.config.PropiedadesLimites;
import co.edu.unbosque.biohub.gateway.controller.dto.ConsumidorDtos.ApiKeyResponse;
import co.edu.unbosque.biohub.gateway.controller.dto.ConsumidorDtos.CambiarLimiteRequest;
import co.edu.unbosque.biohub.gateway.controller.dto.ConsumidorDtos.ConsumidorResponse;
import co.edu.unbosque.biohub.gateway.controller.dto.ConsumidorDtos.CrearConsumidorRequest;
import co.edu.unbosque.biohub.gateway.controller.dto.ConsumidorDtos.EmitirKeyRequest;
import co.edu.unbosque.biohub.gateway.controller.dto.ConsumidorDtos.KeyEmitidaResponse;
import co.edu.unbosque.biohub.gateway.controller.dto.PaginaResponse;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorExterno;
import co.edu.unbosque.biohub.gateway.service.ServicioApiKeys;
import co.edu.unbosque.biohub.gateway.service.ServicioConsumidores;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/** Administración de consumidores externos y sus keys (HU-12). Solo rol administrador: ver SecurityConfig. */
@RestController
public class ConsumidoresController {

    private final ServicioConsumidores consumidores;
    private final ServicioApiKeys apiKeys;
    private final PropiedadesLimites limites;

    public ConsumidoresController(ServicioConsumidores consumidores, ServicioApiKeys apiKeys,
                                  PropiedadesLimites limites) {
        this.consumidores = consumidores;
        this.apiKeys = apiKeys;
        this.limites = limites;
    }

    @PostMapping("/api/gateway/consumidores")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsumidorResponse crear(@Valid @RequestBody CrearConsumidorRequest solicitud,
                                    @AuthenticationPrincipal Jwt administrador) {
        return respuesta(consumidores.crear(solicitud.nombre(), solicitud.contactoEmail(),
                solicitud.limitePorMinuto(), administrador.getSubject()));
    }

    @GetMapping("/api/gateway/consumidores")
    public PaginaResponse<ConsumidorResponse> listar(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                     @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<ConsumidorExterno> pagina = consumidores.listar(page, size);
        return PaginaResponse.de(pagina.getContent().stream().map(this::respuesta).toList(), page, size,
                pagina.getTotalElements());
    }

    @GetMapping("/api/gateway/consumidores/{id}")
    public ConsumidorResponse obtener(@PathVariable UUID id) {
        return respuesta(consumidores.obtener(id));
    }

    @PutMapping("/api/gateway/consumidores/{id}/limite")
    public ConsumidorResponse cambiarLimite(@PathVariable UUID id, @Valid @RequestBody CambiarLimiteRequest solicitud,
                                            @AuthenticationPrincipal Jwt administrador) {
        return respuesta(consumidores.cambiarLimite(id, solicitud.limitePorMinuto(), administrador.getSubject()));
    }

    /** La key en claro solo sale aquí, una vez. {@code no-store} evita que quede en cachés. */
    @PostMapping("/api/gateway/consumidores/{id}/keys")
    public ResponseEntity<KeyEmitidaResponse> emitirKey(@PathVariable UUID id,
                                                        @Valid @RequestBody(required = false) EmitirKeyRequest solicitud,
                                                        @AuthenticationPrincipal Jwt administrador) {
        var expiraEn = solicitud == null ? null : solicitud.expiraEn();
        KeyEmitidaResponse cuerpo = KeyEmitidaResponse.de(apiKeys.emitir(id, expiraEn, administrador.getSubject()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(cuerpo);
    }

    @GetMapping("/api/gateway/consumidores/{id}/keys")
    public List<ApiKeyResponse> listarKeys(@PathVariable UUID id) {
        return apiKeys.listar(id).stream().map(ApiKeyResponse::de).toList();
    }

    /** Revoca la key. Es idempotente: repetirlo también responde 204. */
    @DeleteMapping("/api/gateway/keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revocarKey(@PathVariable UUID id, @AuthenticationPrincipal Jwt administrador) {
        apiKeys.revocar(id, administrador.getSubject());
    }

    private ConsumidorResponse respuesta(ConsumidorExterno consumidor) {
        return ConsumidorResponse.de(consumidor, limites.porConsumidor());
    }
}
