package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
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
        assertThat(new PropiedadesCifrado("v1:" + K1, "v1", false, null, null, null).toString()).doesNotContain(K1);
        assertThat(new ProveedorDeLlavesEntorno("v1:" + K1, "v1").toString()).doesNotContain(K1);
    }

    // --- Arranque del contexto de Spring ---

    @Import(ConfiguracionUbicacion.class)
    @EnableConfigurationProperties(PropiedadesCifrado.class)
    static class Cfg {
    }

    // La configuración también ensambla el repositorio decorado (ARQ-10): se le dan colaboradores simulados
    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Cfg.class)
            .withBean(co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgoRepository.class,
                    () -> org.mockito.Mockito.mock(co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgoRepository.class))
            .withBean(jakarta.persistence.EntityManager.class, () -> org.mockito.Mockito.mock(jakarta.persistence.EntityManager.class))
            .withBean(co.edu.unbosque.biohub.cientifico.ubicacion.service.AuditoriaAccesoUbicacion.class);

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
        // ambas cosas y BD local: arranca con una llave efímera con un id que no puede ser el de una llave real
        runner.withPropertyValues("spring.profiles.active=dev", "biohub.ubicacion.cifrado.llave-efimera-dev=true",
                        "spring.datasource.url=jdbc:mysql://localhost:3307/cientifico")
                .run(ctx -> assertThat(ctx.getBean(ProveedorDeLlaves.class).versionActiva()).isEqualTo("v9999"));
    }

    @Test
    @DisplayName("SEC-J-06: la llave efímera se niega con una BD remota o sin URL conocida (RNF9, ASR6)")
    void laLlaveEfimeraSeNiegaConUnaBaseNoLocal() {
        for (String url : new String[] {"jdbc:mysql://mysql-x.a.aivencloud.com:12345/cientifico", "jdbc:mysql://10.0.0.5:3306/c",
                "jdbc:mysql://localhost.evil.com/c?x=//localhost", ""}) {
            runner.withPropertyValues("spring.profiles.active=dev", "biohub.ubicacion.cifrado.llave-efimera-dev=true",
                            "spring.datasource.url=" + url)
                    .run(ctx -> {
                        assertThat(ctx).as(url).hasFailed();
                        assertThat(rootMessage(ctx.getStartupFailure())).contains("UBICACION_LLAVE_EFIMERA_DEV solo se permite con una BD local");
                    });
        }
        assertThat(ConfiguracionUbicacion.esBaseLocal("jdbc:mysql://127.0.0.1:3306/c")).isTrue();
        assertThat(ConfiguracionUbicacion.esBaseLocal("jdbc:mysql://[::1]:3306/c")).isTrue();
        assertThat(ConfiguracionUbicacion.esBaseLocal(null)).isFalse();
    }

    @Test
    @DisplayName("SEC-J-06: fuera del perfil dev, sin llaves el servicio no arranca (aunque se pida la efímera)")
    void sinLlavesNoArrancaEnPerfilesDistintosDeDev() {
        for (String perfil : new String[] {"local", "aiven", "prod", "default"}) {
            runner.withPropertyValues("spring.profiles.active=" + perfil, "biohub.ubicacion.cifrado.llave-efimera-dev=true",
                            "spring.datasource.url=jdbc:mysql://localhost:3307/cientifico")
                    .run(ctx -> {
                        assertThat(ctx).as(perfil).hasFailed();
                        assertThat(rootMessage(ctx.getStartupFailure()))
                                .contains("UBICACION_CLAVE_ACTIVA no está configurada o no existe en UBICACION_CLAVES");
                    });
        }
    }

    @Test
    @DisplayName("SEC-J-06: el id de la llave efímera no coincide con los de llaves reales y las llaves reales ganan al efímero")
    void laLlaveEfimeraNoUsaElIdDeUnaLlaveReal() {
        ProveedorDeLlavesEntorno efimera = ProveedorDeLlavesEntorno.efimera();
        assertThat(efimera.versionActiva()).isEqualTo(ProveedorDeLlavesEntorno.ID_EFIMERA).isNotEqualTo("v1");
        assertThat(efimera.versiones()).containsExactly("v9999");
        // con llaves reales configuradas, la bandera efímera se ignora (el proveedor real prevalece)
        runner.withPropertyValues("spring.profiles.active=dev", "biohub.ubicacion.cifrado.llave-efimera-dev=true",
                        "biohub.ubicacion.cifrado.claves=v1:" + K1, "biohub.ubicacion.cifrado.clave-activa=v1")
                .run(ctx -> assertThat(ctx.getBean(ProveedorDeLlaves.class).versionActiva()).isEqualTo("v1"));
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return String.valueOf(t.getMessage());
    }
}
