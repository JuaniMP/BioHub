package co.edu.unbosque.biohub.gateway.controller;

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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.gateway.controller.dto.PaginaResponse;
import co.edu.unbosque.biohub.gateway.controller.dto.UsuarioDtos.CambiarEstadoRequest;
import co.edu.unbosque.biohub.gateway.controller.dto.UsuarioDtos.CambiarRolRequest;
import co.edu.unbosque.biohub.gateway.controller.dto.UsuarioDtos.CrearUsuarioRequest;
import co.edu.unbosque.biohub.gateway.controller.dto.UsuarioDtos.UsuarioCreadoResponse;
import co.edu.unbosque.biohub.gateway.controller.dto.UsuarioDtos.UsuarioResponse;
import co.edu.unbosque.biohub.gateway.domain.ClienteIdentidad;
import co.edu.unbosque.biohub.gateway.service.ServicioUsuarios;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Administración de usuarios internos (HU-10, RF17, RF19). Solo rol administrador: ver SecurityConfig. */
@RestController
@RequestMapping("/api/gateway/usuarios")
public class UsuariosController {

    private final ServicioUsuarios usuarios;

    public UsuariosController(ServicioUsuarios usuarios) {
        this.usuarios = usuarios;
    }

    @GetMapping
    public PaginaResponse<UsuarioResponse> listar(@RequestParam(required = false) @Size(max = 100) String busqueda,
                                                  @RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size) {
        ClienteIdentidad.Pagina pagina = usuarios.listar(busqueda, page, size);
        return PaginaResponse.de(pagina.usuarios().stream().map(UsuarioResponse::de).toList(), page, size,
                pagina.total());
    }

    @GetMapping("/{id}")
    public UsuarioResponse obtener(@PathVariable String id) {
        return UsuarioResponse.de(usuarios.obtener(id));
    }

    /** La contraseña temporal solo sale aquí, una vez. */
    @PostMapping
    public ResponseEntity<UsuarioCreadoResponse> crear(@Valid @RequestBody CrearUsuarioRequest solicitud,
                                                       @AuthenticationPrincipal Jwt administrador) {
        ServicioUsuarios.UsuarioCreado creado = usuarios.crear(solicitud.aDominio(), administrador.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(new UsuarioCreadoResponse(creado.id(), creado.contrasenaTemporal()));
    }

    @PutMapping("/{id}/rol")
    public UsuarioResponse cambiarRol(@PathVariable String id, @Valid @RequestBody CambiarRolRequest solicitud,
                                      @AuthenticationPrincipal Jwt administrador) {
        return UsuarioResponse.de(usuarios.cambiarRol(id, solicitud.rol(), administrador.getSubject()));
    }

    /** RF19: revoca el rol. El usuario conserva su cuenta, pero no puede entrar a ningún módulo interno. */
    @DeleteMapping("/{id}/rol")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarRol(@PathVariable String id, @AuthenticationPrincipal Jwt administrador) {
        usuarios.quitarRol(id, administrador.getSubject());
    }

    @PutMapping("/{id}/estado")
    public UsuarioResponse cambiarEstado(@PathVariable String id, @Valid @RequestBody CambiarEstadoRequest solicitud,
                                         @AuthenticationPrincipal Jwt administrador) {
        return UsuarioResponse.de(usuarios.cambiarEstado(id, solicitud.activo(), administrador.getSubject()));
    }
}
