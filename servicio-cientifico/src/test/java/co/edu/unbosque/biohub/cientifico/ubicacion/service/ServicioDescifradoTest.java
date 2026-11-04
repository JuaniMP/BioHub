package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;

class ServicioDescifradoTest {

    @Test
    void sinRolAltoRangoNoSeTocaElCifrador() {
        RepositorioUbicaciones repositorio = mock(RepositorioUbicaciones.class);
        ServicioDescifrado servicio = new ServicioDescifrado(new PoliticaAccesoUbicacion(), repositorio);
        TestingAuthenticationToken admin = new TestingAuthenticationToken("a", "x", "ROLE_administrador");
        admin.setAuthenticated(true);
        assertThatThrownBy(() -> servicio.descifrar(admin, UUID.randomUUID(),
                new CoordenadasCifradas(new byte[32], new byte[12], "v1")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repositorio);
    }

    @Test
    void siElCifradorFallaDevuelveVacio() {
        RepositorioUbicaciones repositorio = mock(RepositorioUbicaciones.class);
        org.mockito.Mockito.when(repositorio.obtenerUbicacionExacta(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException(
                        co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException.Motivo.INTEGRIDAD));
        ServicioDescifrado servicio = new ServicioDescifrado(new PoliticaAccesoUbicacion(), repositorio);
        TestingAuthenticationToken alto = new TestingAuthenticationToken("a", "x", "ROLE_cientifico_alto_rango");
        alto.setAuthenticated(true);
        assertThat(servicio.descifrar(alto, UUID.randomUUID(), new CoordenadasCifradas(new byte[32], new byte[12], "v1"))).isEmpty();
    }
}
