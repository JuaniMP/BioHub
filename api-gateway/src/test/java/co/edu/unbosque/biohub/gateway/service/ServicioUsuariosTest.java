package co.edu.unbosque.biohub.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import co.edu.unbosque.biohub.gateway.domain.ClienteIdentidad;
import co.edu.unbosque.biohub.gateway.domain.RegistroRevocaciones;
import co.edu.unbosque.biohub.gateway.domain.RolInterno;
import co.edu.unbosque.biohub.gateway.domain.UsuarioInterno;

class ServicioUsuariosTest {

    private final ClienteIdentidad identidad = mock(ClienteIdentidad.class);
    private final RegistroRevocaciones revocaciones = mock(RegistroRevocaciones.class);
    private final ServicioUsuarios servicio = new ServicioUsuarios(identidad, revocaciones);

    @BeforeEach
    void usuarioExistente() {
        given(identidad.buscar("u1")).willReturn(Optional.of(new UsuarioInterno("u1", "ana", "a@b.co", "Ana", "Ruiz",
                true, List.of(RolInterno.ANALISTA_AMBIENTAL))));
    }

    @Test
    void crearGeneraUnaContrasenaTemporalQueCumpleLaPolitica() {
        given(identidad.crear(any(), anyString())).willReturn("nuevo");
        ClienteIdentidad.NuevoUsuario nuevo = new ClienteIdentidad.NuevoUsuario("ana", "a@b.co", "Ana", "Ruiz",
                RolInterno.ANALISTA_AMBIENTAL);

        ServicioUsuarios.UsuarioCreado creado = servicio.crear(nuevo, "sub-admin");

        assertThat(creado.contrasenaTemporal()).hasSizeBetween(12, 64);
        assertThat(creado.toString()).doesNotContain(creado.contrasenaTemporal());
        verify(identidad).crear(nuevo, creado.contrasenaTemporal());
    }

    @Test
    void cambiarRolInvalidaLosTokensAnterioresYCierraSesiones() {
        servicio.cambiarRol("u1", RolInterno.AUDITOR_CIENTIFICO, "sub-admin");

        InOrder orden = inOrder(identidad, revocaciones);
        orden.verify(identidad).asignarRol("u1", RolInterno.AUDITOR_CIENTIFICO);
        orden.verify(revocaciones).marcar("u1");
        orden.verify(identidad).cerrarSesiones("u1");
    }

    @Test
    void quitarRolTambienAplicaDeInmediato() {
        servicio.quitarRol("u1", "sub-admin");

        verify(identidad).quitarRoles("u1");
        verify(revocaciones).marcar("u1");
    }

    @Test
    void desactivarAplicaDeInmediatoYReactivarNo() {
        servicio.cambiarEstado("u1", false, "sub-admin");
        verify(revocaciones).marcar("u1");

        servicio.cambiarEstado("u1", true, "sub-admin");
        verify(identidad).cambiarEstado("u1", true);
        verify(identidad).cerrarSesiones("u1");
    }

    @Test
    void unAdministradorNoPuedeModificarseASiMismo() {
        assertThatThrownBy(() -> servicio.cambiarRol("u1", RolInterno.AUDITOR_CIENTIFICO, "u1"))
                .isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.quitarRol("u1", "u1")).isInstanceOf(ConflictoException.class);
        assertThatThrownBy(() -> servicio.cambiarEstado("u1", false, "u1")).isInstanceOf(ConflictoException.class);

        verify(identidad, never()).asignarRol(any(), any());
        verify(identidad, never()).quitarRoles(any());
        verify(identidad, never()).cambiarEstado(any(), eq(false));
    }

    @Test
    void usuarioInexistenteDa404() {
        given(identidad.buscar("nadie")).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.cambiarRol("nadie", RolInterno.AUDITOR_CIENTIFICO, "sub-admin"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
