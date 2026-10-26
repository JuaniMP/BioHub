package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

class PoliticaAccesoUbicacionTest {

    private final PoliticaAccesoUbicacion politica = new PoliticaAccesoUbicacion();

    private static Authentication con(String... roles) {
        Authentication a = new TestingAuthenticationToken("u", "x",
                java.util.Arrays.stream(roles).map(r -> "ROLE_" + r).toArray(String[]::new));
        a.setAuthenticated(true);
        return a;
    }

    @Test
    void soloAltoRangoVeCoordenadas() {
        assertThat(politica.puedeVerCoordenadas(con("cientifico_alto_rango"))).isTrue();
        assertThat(politica.puedeVerCoordenadas(con("cientifico_alto_rango", "investigador_campo"))).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"investigador_campo", "auditor_cientifico", "analista_ambiental", "administrador", "otro"})
    void losDemasRolesNoVenCoordenadas(String rol) {
        assertThat(politica.puedeVerCoordenadas(con(rol))).isFalse();
        assertThatThrownBy(() -> politica.exigirCoordenadas(con(rol))).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void sinAutenticacionOAnonimoNoVeCoordenadas() {
        assertThat(politica.puedeVerCoordenadas(null)).isFalse();
        assertThat(politica.puedeVerCoordenadas(new AnonymousAuthenticationToken("k", "anon",
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_cientifico_alto_rango"))))).isFalse();
        Authentication noAutenticada = new TestingAuthenticationToken("u", "x", AuthorityUtils.createAuthorityList("ROLE_cientifico_alto_rango"));
        noAutenticada.setAuthenticated(false);
        assertThat(politica.puedeVerCoordenadas(noAutenticada)).isFalse();
    }

    @Test
    void elRolDebeSerExacto() {
        assertThat(politica.puedeVerCoordenadas(con("cientifico_alto_rango_x"))).isFalse();
        assertThat(politica.puedeVerCoordenadas(con("CIENTIFICO_ALTO_RANGO"))).isFalse();
    }
}
