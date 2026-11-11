package co.edu.unbosque.biohub.gateway.domain;

import java.util.List;
import java.util.Optional;

/**
 * Puerto hacia el servidor de identidad. El resto del gateway no sabe que detrás está Keycloak.
 * Un fallo de comunicación se informa con IdentidadNoDisponibleException.
 */
public interface ClienteIdentidad {

    record Pagina(List<UsuarioInterno> usuarios, long total) {
    }

    record NuevoUsuario(String usuario, String email, String nombre, String apellido, RolInterno rol) {
    }

    Pagina listar(String busqueda, int pagina, int tamano);

    Optional<UsuarioInterno> buscar(String id);

    /** Crea el usuario con su único rol y una contraseña temporal. Devuelve su id. */
    String crear(NuevoUsuario usuario, String contrasenaTemporal);

    /** Deja al usuario con exactamente este rol. */
    void asignarRol(String id, RolInterno rol);

    void quitarRoles(String id);

    void cambiarEstado(String id, boolean activo);

    void cerrarSesiones(String id);
}
