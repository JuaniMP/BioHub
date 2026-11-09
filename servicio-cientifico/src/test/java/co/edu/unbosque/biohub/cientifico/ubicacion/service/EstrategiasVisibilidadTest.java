package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.FilaHallazgo;

/** ARQ-09 (RF12, RF13, D41): Strategy de visibilidad de la ubicación y su contexto, la política de acceso. */
class EstrategiasVisibilidadTest {

    private static final UUID ID = UUID.randomUUID();
    private static final CoordenadasCifradas CIFRADAS = new CoordenadasCifradas(new byte[32], new byte[12], "v1");

    private final ServicioDescifrado descifrado = mock(ServicioDescifrado.class);

    private static Authentication con(String... roles) {
        Authentication a = new TestingAuthenticationToken("u", "x",
                java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new));
        a.setAuthenticated(true);
        return a;
    }

    private static FilaHallazgo fila(boolean conCoordenadas, boolean conCifradas) {
        return new FilaHallazgo(ID, "Atelopus ebenoides", LocalDate.of(2026, 3, 14), "inv",
                EstadoConservacion.EN_PELIGRO, "Boyacá", "15", "Andina", conCoordenadas,
                conCifradas ? CIFRADAS : null);
    }

    @SuppressWarnings("unchecked")
    private PoliticaAccesoUbicacion politica() {
        ObjectProvider<ServicioDescifrado> proveedor = mock(ObjectProvider.class);
        when(proveedor.getObject()).thenReturn(descifrado);
        return new PoliticaAccesoUbicacion(proveedor);
    }

    @Test
    @DisplayName("ARQ-09 RF13: VisibilidadGeneralizada devuelve departamento y región, nunca coordenadas y nunca invoca el descifrado")
    void generalizadaNuncaDescifra() {
        EstrategiaVisibilidadUbicacion g = new VisibilidadGeneralizada();
        UbicacionResuelta u = g.resolver(con("investigador_campo"), fila(true, true));
        assertThat(u.estado()).isEqualTo(UbicacionResuelta.Estado.PROTEGIDAS);
        assertThat(u.protegida()).isTrue();
        assertThat(u.departamento()).isEqualTo("Boyacá");
        assertThat(u.departamentoCodigo()).isEqualTo("15");
        assertThat(u.region()).isEqualTo("Andina");
        assertThat(u.latitud()).isNull();
        assertThat(u.longitud()).isNull();
        assertThat(g.necesitaCoordenadasCifradas()).isFalse();
        assertThat(g.resolver(null, fila(false, false)).estado()).isEqualTo(UbicacionResuelta.Estado.SIN_COORDENADAS);
        verifyNoInteractions(descifrado);
    }

    @Test
    @DisplayName("ARQ-09 RF12: VisibilidadExacta entrega las coordenadas descifradas por el servicio de descifrado")
    void exactaEntregaCoordenadas() {
        Authentication alto = con("cientifico_alto_rango");
        when(descifrado.descifrar(alto, ID, CIFRADAS)).thenReturn(Optional.of(new Coordenadas(5.5, -73.5)));
        EstrategiaVisibilidadUbicacion e = new VisibilidadExacta(descifrado);
        UbicacionResuelta u = e.resolver(alto, fila(true, true));
        assertThat(u.estado()).isEqualTo(UbicacionResuelta.Estado.VISIBLES);
        assertThat(u.latitud()).isEqualTo(5.5);
        assertThat(u.longitud()).isEqualTo(-73.5);
        assertThat(u.protegida()).isFalse();
        assertThat(u.toString()).doesNotContain("5.5").doesNotContain("73.5");
        assertThat(e.necesitaCoordenadasCifradas()).isTrue();
    }

    @Test
    @DisplayName("ARQ-09: la exacta distingue SIN_COORDENADAS y NO_DISPONIBLES (llave retirada o dato alterado)")
    void exactaSinCoordenadasONoDisponibles() {
        Authentication alto = con("cientifico_alto_rango");
        EstrategiaVisibilidadUbicacion e = new VisibilidadExacta(descifrado);
        assertThat(e.resolver(alto, fila(false, false)).estado()).isEqualTo(UbicacionResuelta.Estado.SIN_COORDENADAS);
        verifyNoInteractions(descifrado);

        when(descifrado.descifrar(any(), any(), any())).thenReturn(Optional.empty());
        UbicacionResuelta u = e.resolver(alto, fila(true, true));
        assertThat(u.estado()).isEqualTo(UbicacionResuelta.Estado.NO_DISPONIBLES);
        assertThat(u.latitud()).isNull();
        // una fila sin texto cifrado (lectura sin cifrado) tampoco inventa coordenadas
        assertThat(e.resolver(alto, fila(true, false)).estado()).isEqualTo(UbicacionResuelta.Estado.NO_DISPONIBLES);
    }

    @Test
    @DisplayName("ARQ-09 RF13: estrategiaPara devuelve la exacta solo para cientifico_alto_rango")
    void soloAltoRangoRecibeLaExacta() {
        PoliticaAccesoUbicacion p = politica();
        assertThat(p.estrategiaPara(con("cientifico_alto_rango"))).isInstanceOf(VisibilidadExacta.class);
        assertThat(p.estrategiaPara(con("cientifico_alto_rango", "investigador_campo"))).isInstanceOf(VisibilidadExacta.class);
        assertThat(p.estrategiaPara(con("cientifico_alto_rango"))).as("se reutiliza").isSameAs(p.estrategiaPara(con("cientifico_alto_rango")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"investigador_campo", "auditor_cientifico", "analista_ambiental", "administrador", "rol_desconocido"})
    @DisplayName("ARQ-09 RF13/D41: cualquier otro rol, o uno desconocido, recibe la generalizada por defecto")
    void losDemasRolesRecibenLaGeneralizada(String rol) {
        assertThat(politica().estrategiaPara(con(rol))).isInstanceOf(VisibilidadGeneralizada.class);
    }

    @Test
    @DisplayName("ARQ-09 RF13: sin autenticación, anónimo o sin autoridades la estrategia es la generalizada (falla segura)")
    void sinAutenticacionEsGeneralizada() {
        PoliticaAccesoUbicacion p = politica();
        assertThat(p.estrategiaPara(null)).isInstanceOf(VisibilidadGeneralizada.class);
        assertThat(p.estrategiaPara(new AnonymousAuthenticationToken("k", "anon",
                java.util.List.of(new SimpleGrantedAuthority("ROLE_cientifico_alto_rango"))))).isInstanceOf(VisibilidadGeneralizada.class);
        assertThat(p.estrategiaPara(con())).isInstanceOf(VisibilidadGeneralizada.class);
        verifyNoInteractions(descifrado);
    }

    @Test
    @DisplayName("ARQ-09: una política sin servicio de descifrado no puede entregar la estrategia exacta")
    void sinServicioDeDescifradoNoHayExacta() {
        PoliticaAccesoUbicacion sinServicio = new PoliticaAccesoUbicacion();
        assertThat(sinServicio.estrategiaPara(con("investigador_campo"))).isInstanceOf(VisibilidadGeneralizada.class);
        assertThatThrownBy(() -> sinServicio.estrategiaPara(con("cientifico_alto_rango")))
                .isInstanceOf(IllegalStateException.class);
    }
}
