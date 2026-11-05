package co.edu.unbosque.biohub.cientifico.registro.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** El Builder es la única puerta de entrada al historial (SAD §6.2.1): aquí se comprueba que la cierra bien. */
class VersionHallazgoBuilderTest {

    private static final UUID HALLAZGO = UUID.randomUUID();
    private static final Map<String, Object> SNAPSHOT = Map.of("nombreCientifico", "Atelopus ebenoides");

    @Test
    @DisplayName("la primera version no necesita justificacion")
    void primeraVersionSinJustificacion() {
        VersionHallazgo version = VersionHallazgo.primeraVersion(HALLAZGO)
                .snapshot(SNAPSHOT)
                .autor("investigador1")
                .build();

        assertThat(version.getVersion()).isEqualTo(1);
        assertThat(version.getJustificacion()).isNull();
        assertThat(version.getHallazgoId()).isEqualTo(HALLAZGO);
    }

    @Test
    @DisplayName("una reclasificacion sin justificacion no se puede construir (HU-03)")
    void reclasificacionExigeJustificacion() {
        VersionHallazgo.Builder builder = VersionHallazgo.siguienteVersion(HALLAZGO, 1)
                .snapshot(SNAPSHOT)
                .autor("cientifico1");

        assertThatThrownBy(builder::build)
                .isInstanceOf(VersionIncompletaException.class)
                .hasMessageContaining("justificacion");
    }

    @Test
    @DisplayName("la version siguiente es la anterior mas uno")
    void numeraLaVersionSiguiente() {
        VersionHallazgo version = VersionHallazgo.siguienteVersion(HALLAZGO, 4)
                .snapshot(SNAPSHOT)
                .justificacion("Se confirmo la especie con pruebas de laboratorio")
                .autor("cientifico1")
                .build();

        assertThat(version.getVersion()).isEqualTo(5);
    }

    @Test
    @DisplayName("una justificacion en blanco cuenta como ausente")
    void justificacionEnBlancoNoVale() {
        VersionHallazgo.Builder builder = VersionHallazgo.siguienteVersion(HALLAZGO, 1)
                .snapshot(SNAPSHOT)
                .justificacion("   ")
                .autor("cientifico1");

        assertThatThrownBy(builder::build).isInstanceOf(VersionIncompletaException.class);
    }

    @Test
    @DisplayName("sin snapshot o sin autor no se construye")
    void exigeSnapshotYAutor() {
        assertThatThrownBy(() -> VersionHallazgo.primeraVersion(HALLAZGO).autor("x").build())
                .isInstanceOf(VersionIncompletaException.class)
                .hasMessageContaining("obligatorio");

        assertThatThrownBy(() -> VersionHallazgo.primeraVersion(HALLAZGO).snapshot(SNAPSHOT).build())
                .isInstanceOf(VersionIncompletaException.class)
                .hasMessageContaining("obligatorio");
    }

    @Test
    @DisplayName("el snapshot que devuelve es una copia: no se puede alterar desde fuera")
    void snapshotEsInmutable() {
        VersionHallazgo version = VersionHallazgo.primeraVersion(HALLAZGO)
                .snapshot(SNAPSHOT)
                .autor("investigador1")
                .build();

        assertThatThrownBy(() -> version.getSnapshot().put("otro", "valor"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatCode(() -> version.getSnapshot().get("nombreCientifico")).doesNotThrowAnyException();
    }
}
