package co.edu.unbosque.biohub.gateway.service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import co.edu.unbosque.biohub.gateway.domain.ClienteIdentidad;
import co.edu.unbosque.biohub.gateway.domain.RegistroRevocaciones;
import co.edu.unbosque.biohub.gateway.domain.RolInterno;
import co.edu.unbosque.biohub.gateway.domain.UsuarioInterno;

/**
 * Usuarios internos y sus roles (HU-10, RF17, RF19). Keycloak sigue siendo el dueño de los datos. Este
 * servicio agrega lo que Keycloak solo no garantiza: exactamente un rol del catálogo y que un cambio de
 * rol se aplique desde la siguiente petición.
 */
@Service
public class ServicioUsuarios {

    private static final Logger auditoria = LoggerFactory.getLogger("biohub.auditoria.gateway");
    private static final int BYTES_CONTRASENA = 18;

    private final ClienteIdentidad identidad;
    private final RegistroRevocaciones revocaciones;
    private final SecureRandom aleatorio = new SecureRandom();

    public ServicioUsuarios(ClienteIdentidad identidad, RegistroRevocaciones revocaciones) {
        this.identidad = identidad;
        this.revocaciones = revocaciones;
    }

    /** La contraseña temporal solo existe en este objeto: Keycloak obliga a cambiarla al primer ingreso. */
    public record UsuarioCreado(String id, String contrasenaTemporal) {
        @Override
        public String toString() {
            return "UsuarioCreado[id=" + id + ", contrasenaTemporal=***]";
        }
    }

    public ClienteIdentidad.Pagina listar(String busqueda, int pagina, int tamano) {
        return identidad.listar(busqueda, pagina, tamano);
    }

    public UsuarioInterno obtener(String id) {
        return identidad.buscar(id).orElseThrow(() -> new RecursoNoEncontradoException("El usuario no existe."));
    }

    public UsuarioCreado crear(ClienteIdentidad.NuevoUsuario usuario, String actor) {
        byte[] bytes = new byte[BYTES_CONTRASENA];
        aleatorio.nextBytes(bytes);
        String contrasena = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String id = identidad.crear(usuario, contrasena);
        auditoria.info("usuario_creado id={} rol={} actor={}", id, usuario.rol().nombre(), actor);
        return new UsuarioCreado(id, contrasena);
    }

    public UsuarioInterno cambiarRol(String id, RolInterno rol, String actor) {
        exigirExistente(id);
        impedirSobreSiMismo(id, actor, "cambiar tu propio rol");
        identidad.asignarRol(id, rol);
        aplicarDeInmediato(id);
        auditoria.info("rol_cambiado id={} rol={} actor={}", id, rol.nombre(), actor);
        return obtener(id);
    }

    public void quitarRol(String id, String actor) {
        exigirExistente(id);
        impedirSobreSiMismo(id, actor, "quitarte tu propio rol");
        identidad.quitarRoles(id);
        aplicarDeInmediato(id);
        auditoria.info("rol_revocado id={} actor={}", id, actor);
    }

    public UsuarioInterno cambiarEstado(String id, boolean activo, String actor) {
        exigirExistente(id);
        impedirSobreSiMismo(id, actor, "desactivar tu propia cuenta");
        identidad.cambiarEstado(id, activo);
        if (!activo) {
            aplicarDeInmediato(id);
        }
        auditoria.info("usuario_estado id={} activo={} actor={}", id, activo, actor);
        return obtener(id);
    }

    private void exigirExistente(String id) {
        obtener(id);
    }

    /** Evita que el último administrador se quede fuera por error. */
    private static void impedirSobreSiMismo(String id, String actor, String accion) {
        if (Objects.equals(id, actor)) {
            throw new ConflictoException("No puedes " + accion + ". Pídeselo a otro administrador.");
        }
    }

    /** Invalida los tokens ya emitidos y las sesiones que podrían pedir tokens nuevos. */
    private void aplicarDeInmediato(String id) {
        revocaciones.marcar(id);
        identidad.cerrarSesiones(id);
    }
}
