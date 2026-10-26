package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.ProveedorDeLlaves;

class ProveedorDeLlavesEntornoTest {

    private static final String K1 = CifradorAesGcmTest.llaveNueva();
    private static final String K2 = CifradorAesGcmTest.llaveNueva();

    @Test
    void cargaVariasLlavesYLaActiva() {
        ProveedorDeLlavesEntorno p = new ProveedorDeLlavesEntorno("v1:" + K1 + ", v2:" + K2, "v2");
        assertThat(p.versionActiva()).isEqualTo("v2");
        assertThat(p.versiones()).containsExactlyInAnyOrder("v1", "v2");
        assertThat(p.llave("v1")).isPresent();
        assertThat(p.llave("v9")).isEmpty();
    }

    @Test
    void sinLlaveActivaNoArranca() {
        assertThatThrownBy(() -> new ProveedorDeLlavesEntorno("v1:" + K1, ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("UBICACION_CLAVE_ACTIVA no está configurada o no existe en UBICACION_CLAVES");
        assertThatThrownBy(() -> new ProveedorDeLlavesEntorno("v1:" + K1, null))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProveedorDeLlavesEntorno("v1:" + K1, "v2"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProveedorDeLlavesEntorno("", "v1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void llaveInvalidaNoArrancaYElMensajeNoFiltraElValor() {
        String corta = Base64.getEncoder().encodeToString(new byte[16]);
        assertThatThrownBy(() -> new ProveedorDeLlavesEntorno("v1:" + corta, "v1"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("UBICACION_CLAVES: la llave 'v1' no es válida")
                .message().doesNotContain(corta);
        assertThatThrownBy(() -> new ProveedorDeLlavesEntorno("v1:no-es-base64!!", "v1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProveedorDeLlavesEntorno("uno:" + K1, "uno"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProveedorDeLlavesEntorno("v1:" + K1 + ",v1:" + K2, "v1"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ProveedorDeLlavesEntorno("v1", "v1"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void propiedadesNoExponenLlavesEnToString() {
        assertThat(new PropiedadesCifrado("v1:" + K1, "v1", false).toString()).doesNotContain(K1);
        assertThat(new ProveedorDeLlavesEntorno("v1:" + K1, "v1").toString()).doesNotContain(K1);
    }

    // --- Arranque del contexto de Spring ---

    @Import(ConfiguracionUbicacion.class)
    @EnableConfigurationProperties(PropiedadesCifrado.class)
    static class Cfg {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Cfg.class);

    @Test
    void elContextoNoArrancaSinLlaves() {
        runner.run(ctx -> {
            assertThat(ctx).hasFailed();
            assertThat(rootMessage(ctx.getStartupFailure()))
                    .contains("UBICACION_CLAVE_ACTIVA no está configurada o no existe en UBICACION_CLAVES");
        });
    }

    @Test
    void elContextoNoArrancaConLlaveInvalida() {
        runner.withPropertyValues("biohub.ubicacion.cifrado.claves=v1:abc", "biohub.ubicacion.cifrado.clave-activa=v1")
                .run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void elContextoArrancaConLlavesValidas() {
        runner.withPropertyValues("biohub.ubicacion.cifrado.claves=v1:" + K1, "biohub.ubicacion.cifrado.clave-activa=v1")
                .run(ctx -> {
                    assertThat(ctx).hasNotFailed();
                    assertThat(ctx).hasSingleBean(CifradorCoordenadas.class);
                    assertThat(ctx.getBean(ProveedorDeLlaves.class).versionActiva()).isEqualTo("v1");
                });
    }

    @Test
    void laLlaveEfimeraSoloConPerfilDevYActivadaExplicitamente() {
        // activada pero sin perfil dev: no arranca
        runner.withPropertyValues("biohub.ubicacion.cifrado.llave-efimera-dev=true")
                .run(ctx -> assertThat(ctx).hasFailed());
        // perfil dev pero sin activarla: no arranca
        runner.withPropertyValues("spring.profiles.active=dev").run(ctx -> assertThat(ctx).hasFailed());
        // ambas cosas: arranca con una llave efímera
        runner.withPropertyValues("spring.profiles.active=dev", "biohub.ubicacion.cifrado.llave-efimera-dev=true")
                .run(ctx -> assertThat(ctx.getBean(ProveedorDeLlaves.class).versionActiva()).isEqualTo("v1"));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
