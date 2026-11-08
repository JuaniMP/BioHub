package co.edu.unbosque.biohub.gateway.controller.dto;

import java.util.List;

import co.edu.unbosque.biohub.gateway.domain.ClienteIdentidad;
import co.edu.unbosque.biohub.gateway.domain.RolInterno;
import co.edu.unbosque.biohub.gateway.domain.UsuarioInterno;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Cuerpos JSON de la administración de usuarios (04-contrato-api.md). */
public final class UsuarioDtos {

    private UsuarioDtos() {
    }

    /** {@code rol} es uno solo y obligatorio: así un usuario nuevo tiene exactamente un rol (HU-10 c.1). */
    public record CrearUsuarioRequest(
            @NotBlank(message = "obligatorio") @Size(min = 3, max = 50, message = "debe tener entre 3 y 50 caracteres")
            @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "solo letras, números, punto, guion y guion bajo")
            String usuario,
            @NotBlank(message = "obligatorio") @Email(message = "correo inválido")
            @Size(max = 254, message = "máximo 254 caracteres") String email,
            @NotBlank(message = "obligatorio") @Size(max = 100, message = "máximo 100 caracteres") String nombre,
            @NotBlank(message = "obligatorio") @Size(max = 100, message = "máximo 100 caracteres") String apellido,
            @NotNull(message = "obligatorio") RolInterno rol) {

        public ClienteIdentidad.NuevoUsuario aDominio() {
            return new ClienteIdentidad.NuevoUsuario(usuario, email, nombre, apellido, rol);
        }
    }

    public record CambiarRolRequest(@NotNull(message = "obligatorio") RolInterno rol) {
    }

    public record CambiarEstadoRequest(@NotNull(message = "obligatorio") Boolean activo) {
    }

    public record UsuarioResponse(String id, String usuario, String email, String nombre, String apellido,
                                  boolean activo, List<RolInterno> roles) {
        public static UsuarioResponse de(UsuarioInterno u) {
            return new UsuarioResponse(u.id(), u.usuario(), u.email(), u.nombre(), u.apellido(), u.activo(), u.roles());
        }
    }

    /** Única respuesta que contiene la contraseña temporal. */
    public record UsuarioCreadoResponse(String id, String contrasenaTemporal) {
        @Override
        public String toString() {
            return "UsuarioCreadoResponse[id=" + id + ", contrasenaTemporal=***]";
        }
    }
}
