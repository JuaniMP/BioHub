package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;

class ServicioDescifradoTest {

    @Test
    void sinRolAltoRangoNoSeTocaElCifrador() {
        CifradorCoordenadas cifrador = mock(CifradorCoordenadas.class);
        ServicioDescifrado servicio = new ServicioDescifrado(new PoliticaAccesoUbicacion(), cifrador);
        TestingAuthenticationToken admin = new TestingAuthenticationToken("a", "x", "ROLE_administrador");
        admin.setAuthenticated(true);
        assertThatThrownBy(() -> servicio.descifrar(admin, UUID.randomUUID(),
                new CoordenadasCifradas(new byte[32], new byte[12], "v1")))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(cifrador);
    }

    @Test
    void siElCifradorFallaDevuelveVacio() {
        CifradorCoordenadas cifrador = mock(CifradorCoordenadas.class);
        org.mockito.Mockito.when(cifrador.descifrar(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException(
                        co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException.Motivo.INTEGRIDAD));
        ServicioDescifrado servicio = new ServicioDescifrado(new PoliticaAccesoUbicacion(), cifrador);
        TestingAuthenticationToken alto = new TestingAuthenticationToken("a", "x", "ROLE_cientifico_alto_rango");
        alto.setAuthenticated(true);
        assertThat(servicio.descifrar(alto, UUID.randomUUID(), new CoordenadasCifradas(new byte[32], new byte[12], "v1"))).isEmpty();
    }
}
