package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Departamento;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgo;

/** Validaciones de {@link UbicacionServiceImpl} con colaboradores simulados (sin BD ni transacción). */
class UbicacionServiceImplValidacionesTest {

    private final RepositorioUbicaciones repo = mock(RepositorioUbicaciones.class);
    private final CatalogoDepartamentos catalogo = mock(CatalogoDepartamentos.class);
    private UbicacionServiceImpl servicio;
    private final UUID id = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void preparar() {
        ObjectProvider<Clock> reloj = mock(ObjectProvider.class);
        // 2026-10-26 02:00 UTC = 2026-10-25 21:00 en Bogotá
        when(reloj.getIfAvailable(any())).thenReturn(Clock.fixed(Instant.parse("2026-10-26T02:00:00Z"), ZoneOffset.UTC));
        servicio = new UbicacionServiceImpl(repo, catalogo, reloj);
        when(catalogo.resolver(any())).thenReturn(Optional.empty());
        when(catalogo.resolver("15")).thenReturn(Optional.of(mock(Departamento.class)));
    }

    private RegistroUbicacionComando cmd(UUID hid, int version, String nombre, LocalDate fecha, String inv,
                                         String depto, EstadoConservacion ec, Double lat, Double lon) {
        return new RegistroUbicacionComando(hid, version, nombre, fecha, inv, depto, ec, lat, lon);
    }

    private void invalido(RegistroUbicacionComando c, String campo, String mensaje) {
        assertThatThrownBy(() -> servicio.registrar(c)).isInstanceOfSatisfying(UbicacionInvalidaException.class, e -> {
            assertThat(e.campo()).isEqualTo(campo);
            assertThat(e.getMessage()).isEqualTo(mensaje);
        });
        verify(repo, never()).guardar(any(), any());
    }

    private static final LocalDate HOY = LocalDate.of(2026, 10, 25);

    @Test
    void validacionesDeRegistrar() {
        invalido(cmd(null, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, null, null), "hallazgoId", "obligatorio");
        invalido(cmd(id, 0, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, null, null), "version", "debe ser mayor o igual a 1");
        invalido(cmd(id, 1, " ", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, null, null), "nombreCientifico", "obligatorio, máximo 150 caracteres");
        invalido(cmd(id, 1, "x".repeat(151), HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, null, null), "nombreCientifico", "obligatorio, máximo 150 caracteres");
        invalido(cmd(id, 1, "A b", null, "inv", "15", EstadoConservacion.EN_PELIGRO, null, null), "fechaRecoleccion", "obligatoria y no futura");
        invalido(cmd(id, 1, "A b", HOY.plusDays(1), "inv", "15", EstadoConservacion.EN_PELIGRO, null, null), "fechaRecoleccion", "obligatoria y no futura");
        invalido(cmd(id, 1, "A b", HOY, "", "15", EstadoConservacion.EN_PELIGRO, null, null), "investigador", "obligatorio, máximo 100 caracteres");
        invalido(cmd(id, 1, "A b", HOY, "i".repeat(101), "15", EstadoConservacion.EN_PELIGRO, null, null), "investigador", "obligatorio, máximo 100 caracteres");
        invalido(cmd(id, 1, "A b", HOY, "inv", "Narnia", EstadoConservacion.EN_PELIGRO, null, null), "departamento", "departamento desconocido");
        invalido(cmd(id, 1, "A b", HOY, "inv", "15", null, null, null), "estadoConservacion", "obligatorio");
        invalido(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, 5.0, null), "latitud", "latitud y longitud van juntas");
        invalido(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, null, 5.0), "latitud", "latitud y longitud van juntas");
        invalido(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, 90.1, 0.0), "latitud", "latitud fuera de rango");
        invalido(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, Double.NaN, 0.0), "latitud", "latitud fuera de rango");
        invalido(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, 0.0, -180.5), "longitud", "longitud fuera de rango");
        invalido(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, 0.0, Double.POSITIVE_INFINITY), "longitud", "longitud fuera de rango");
    }

    @Test
    @DisplayName("SEC-J-02: el nombre científico no puede traer coordenadas en texto libre (RF11, RNF9)")
    void nombreCientificoConCoordenadasSeRechazaSinEcoDelValor() {
        for (String nombre : new String[] {"Atelopus 5.53214, -73.36712", "Atelopus 5°31'55\"N", "Atelopus lat: 5.5 lon: -73.3"}) {
            assertThatThrownBy(() -> servicio.registrar(cmd(id, 1, nombre, HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, null, null)))
                    .isInstanceOfSatisfying(UbicacionInvalidaException.class, e -> {
                        assertThat(e.campo()).isEqualTo("nombreCientifico");
                        assertThat(e.getMessage()).isEqualTo("no puede contener coordenadas").doesNotContain("5.5");
                    });
            assertThatThrownBy(() -> servicio.actualizar(new ActualizacionUbicacionComando(id, 2, nombre, HOY, "15",
                    EstadoConservacion.EN_PELIGRO)))
                    .isInstanceOfSatisfying(UbicacionInvalidaException.class, e -> assertThat(e.campo()).isEqualTo("nombreCientifico"));
        }
        verify(repo, never()).guardar(any(), any());
        // nombres habituales (con números, subespecies, autoría y año) no son coordenadas
        for (String nombre : new String[] {"Atelopus ebenoides marinkellei", "Rhinella marina (Linnaeus, 1758)", "Boana sp. 2",
                "Pristimantis cf. 3.200"}) {
            assertThat(servicio.registrar(cmd(UUID.randomUUID(), 1, nombre, HOY, "inv", "15", EstadoConservacion.SIN_EVALUAR, null, null)))
                    .isEqualTo(ResultadoUbicacion.APLICADA);
        }
    }

    @Test
    void laFechaDeHoySeEvaluaEnZonaBogota() {
        // En UTC ya es 26, pero en Bogotá sigue siendo 25: el 26 es futuro
        invalido(cmd(id, 1, "A b", LocalDate.of(2026, 10, 26), "inv", "15", EstadoConservacion.EN_PELIGRO, null, null),
                "fechaRecoleccion", "obligatoria y no futura");
        assertThat(servicio.registrar(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, null, null)))
                .isEqualTo(ResultadoUbicacion.APLICADA);
    }

    @Test
    void duplicadoSeIgnoraSinCifrarNiGuardar() {
        when(repo.existe(id)).thenReturn(true);
        assertThat(servicio.registrar(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.SIN_EVALUAR, 1.0, 2.0)))
                .isEqualTo(ResultadoUbicacion.IGNORADA_DUPLICADA);
        verify(repo, never()).guardar(any(), any());
    }

    @Test
    void sinCoordenadasNoSeCifra() {
        servicio.registrar(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.SIN_EVALUAR, null, null));
        verify(repo).guardar(any(UbicacionHallazgo.class), isNull());
    }

    @Test
    @DisplayName("ARQ-10: el servicio entrega las coordenadas en claro al repositorio decorado (que cifra) y no cifra por su cuenta (RNF9)")
    void conCoordenadasSeEntreganAlRepositorioDecorado() {
        servicio.registrar(cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, 5.5, -73.5));
        org.mockito.ArgumentCaptor<co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas> c =
                org.mockito.ArgumentCaptor.forClass(co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas.class);
        verify(repo).guardar(any(UbicacionHallazgo.class), c.capture());
        assertThat(c.getValue()).isEqualTo(new co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas(5.5, -73.5));
    }

    @Test
    void validacionesYResultadosDeActualizar() {
        ActualizacionUbicacionComando base = new ActualizacionUbicacionComando(id, 2, "A b", HOY, "15", EstadoConservacion.EN_PELIGRO);
        assertThatThrownBy(() -> servicio.actualizar(new ActualizacionUbicacionComando(id, 2, "A b", HOY, "15", null)))
                .isInstanceOfSatisfying(UbicacionInvalidaException.class, e -> assertThat(e.campo()).isEqualTo("estadoConservacion"));
        assertThatThrownBy(() -> servicio.actualizar(new ActualizacionUbicacionComando(id, 2, "A b", HOY, "zz", EstadoConservacion.EN_PELIGRO)))
                .isInstanceOfSatisfying(UbicacionInvalidaException.class, e -> assertThat(e.campo()).isEqualTo("departamento"));
        when(repo.porId(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> servicio.actualizar(base)).isInstanceOf(UbicacionNoEncontradaException.class);

        UbicacionHallazgo fila = new UbicacionHallazgo(id, 2, "15", "Viejo", HOY, "inv", EstadoConservacion.SIN_EVALUAR);
        when(repo.porId(id)).thenReturn(Optional.of(fila));
        assertThat(servicio.actualizar(base)).isEqualTo(ResultadoUbicacion.IGNORADA_VERSION_ANTIGUA); // 2 <= 2
        assertThat(fila.getNombreCientifico()).isEqualTo("Viejo");
        assertThat(servicio.actualizar(new ActualizacionUbicacionComando(id, 3, "Nuevo", HOY, "15", EstadoConservacion.EN_PELIGRO)))
                .isEqualTo(ResultadoUbicacion.APLICADA);
        assertThat(fila.getNombreCientifico()).isEqualTo("Nuevo");
        assertThat(fila.getVersion()).isEqualTo(3);
        assertThat(fila.getEstadoConservacion()).isEqualTo(EstadoConservacion.EN_PELIGRO);
    }

    @Test
    void elComandoYLaEntidadNoImprimenCoordenadas() {
        RegistroUbicacionComando c = cmd(id, 1, "A b", HOY, "inv", "15", EstadoConservacion.EN_PELIGRO, 5.1234567, -73.7654321);
        assertThat(c.toString()).doesNotContain("5.1234567").doesNotContain("73.7654321").contains("coordenadas=***");
        assertThat(new UbicacionHallazgo(id, 1, "15", "A b", HOY, "inv", EstadoConservacion.EN_PELIGRO).toString())
                .contains("coordenadas=***");
    }
}
