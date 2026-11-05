package co.edu.unbosque.biohub.cientifico.registro.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import co.edu.unbosque.biohub.cientifico.registro.domain.CambioAuditoriaRepository;
import co.edu.unbosque.biohub.cientifico.registro.domain.VersionHallazgo;
import co.edu.unbosque.biohub.cientifico.registro.domain.VersionHallazgoRepository;

/** HU-05: el auditor compara dos versiones y ve resaltado solo lo que cambió. */
class HistorialServiceTest {

    private static final UUID HALLAZGO = UUID.randomUUID();

    private VersionHallazgoRepository versiones;
    private HistorialService servicio;

    @BeforeEach
    void prepararServicio() {
        versiones = Mockito.mock(VersionHallazgoRepository.class);
        servicio = new HistorialService(versiones, Mockito.mock(CambioAuditoriaRepository.class));
    }

    private static VersionHallazgo version(int numero, String estado, String nombre) {
        VersionHallazgo.Builder builder = numero == 1
                ? VersionHallazgo.primeraVersion(HALLAZGO)
                : VersionHallazgo.siguienteVersion(HALLAZGO, numero - 1).justificacion("Revision taxonomica");
        return builder
                .snapshot(Map.of("version", numero, "estadoConservacion", estado, "nombreCientifico", nombre,
                        "familia", "Bufonidae"))
                .autor("cientifico1")
                .build();
    }

    @Test
    @DisplayName("comparar devuelve solo los campos que cambiaron")
    void comparaSoloLosCambios() {
        when(versiones.findByHallazgoIdAndVersion(HALLAZGO, 1))
                .thenReturn(Optional.of(version(1, "NO_AMENAZADA", "Atelopus ebenoides")));
        when(versiones.findByHallazgoIdAndVersion(HALLAZGO, 2))
                .thenReturn(Optional.of(version(2, "EN_PELIGRO", "Atelopus ebenoides")));

        ComparacionVersiones comparacion = servicio.comparar(HALLAZGO, 1, 2);

        assertThat(comparacion.diferencias()).hasSize(1);
        DiferenciaCampo cambio = comparacion.diferencias().get(0);
        assertThat(cambio.campo()).isEqualTo("estadoConservacion");
        assertThat(cambio.valorAnterior()).isEqualTo("NO_AMENAZADA");
        assertThat(cambio.valorNuevo()).isEqualTo("EN_PELIGRO");
        assertThat(comparacion.justificacion()).isEqualTo("Revision taxonomica");
    }

    @Test
    @DisplayName("el orden de los argumentos no importa: siempre compara de la menor a la mayor")
    void elOrdenNoImporta() {
        when(versiones.findByHallazgoIdAndVersion(HALLAZGO, 1))
                .thenReturn(Optional.of(version(1, "NO_AMENAZADA", "Atelopus ebenoides")));
        when(versiones.findByHallazgoIdAndVersion(HALLAZGO, 2))
                .thenReturn(Optional.of(version(2, "EN_PELIGRO", "Atelopus ebenoides")));

        ComparacionVersiones alReves = servicio.comparar(HALLAZGO, 2, 1);

        assertThat(alReves.versionAnterior()).isEqualTo(1);
        assertThat(alReves.versionNueva()).isEqualTo(2);
    }

    @Test
    @DisplayName("comparar una version consigo misma no tiene sentido")
    void rechazaCompararLaMisma() {
        assertThatThrownBy(() -> servicio.comparar(HALLAZGO, 2, 2))
                .isInstanceOf(DatosHallazgoInvalidosException.class)
                .hasMessageContaining("distintas");
    }

    @Test
    @DisplayName("si una version no existe, se informa como no encontrado")
    void versionInexistente() {
        when(versiones.findByHallazgoIdAndVersion(HALLAZGO, 1))
                .thenReturn(Optional.of(version(1, "NO_AMENAZADA", "Atelopus ebenoides")));
        when(versiones.findByHallazgoIdAndVersion(HALLAZGO, 9)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.comparar(HALLAZGO, 1, 9))
                .isInstanceOf(HallazgoNoEncontradoException.class);
    }

    @Test
    @DisplayName("un hallazgo sin historial se informa como no encontrado")
    void sinHistorial() {
        when(versiones.findByHallazgoIdOrderByVersionDesc(HALLAZGO)).thenReturn(List.of());

        assertThatThrownBy(() -> servicio.versionesDe(HALLAZGO))
                .isInstanceOf(HallazgoNoEncontradoException.class);
    }
}
