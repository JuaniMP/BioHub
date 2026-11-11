package co.edu.unbosque.biohub.gateway.domain;

import java.util.List;

/** Usuario interno tal como lo guarda Keycloak. {@code id} es el mismo {@code sub} de sus tokens. */
public record UsuarioInterno(String id, String usuario, String email, String nombre, String apellido, boolean activo,
                             List<RolInterno> roles) {
}
