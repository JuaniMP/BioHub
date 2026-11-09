package co.edu.unbosque.biohub.gateway.infrastructure.identidad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import co.edu.unbosque.biohub.gateway.config.PropiedadesIdentidad;
import co.edu.unbosque.biohub.gateway.domain.ClienteIdentidad;
import co.edu.unbosque.biohub.gateway.domain.RolInterno;
import co.edu.unbosque.biohub.gateway.domain.UsuarioInterno;
import co.edu.unbosque.biohub.gateway.service.ConflictoException;
import co.edu.unbosque.biohub.gateway.service.DatosRechazadosException;
import co.edu.unbosque.biohub.gateway.service.IdentidadNoDisponibleException;

/**
 * Prueba el realm real de infra/keycloak y el adaptador contra un Keycloak real y desechable:
 * HU-10, HU-11 c.2 (bloqueo a los 3 intentos), RF17 y RF19.
 */
@Testcontainers(disabledWithoutDocker = true)
class KeycloakAdminAdapterIntegracionTest {

    private static final String SECRETO = "secreto-de-prueba-gateway";
    private static final String CONTRASENA_PRUEBA = "contrasena-de-prueba-12";

    @Container
    static final GenericContainer<?> KEYCLOAK = new GenericContainer<>("quay.io/keycloak/keycloak:26.8.1")
            .withCommand("start-dev", "--import-realm")
            .withEnv("KC_BOOTSTRAP_ADMIN_USERNAME", "admin")
            .withEnv("KC_BOOTSTRAP_ADMIN_PASSWORD", "admin-de-prueba")
            .withEnv("KEYCLOAK_TEST_USERS_PASSWORD", CONTRASENA_PRUEBA)
            .withEnv("KEYCLOAK_GATEWAY_CLIENT_SECRET", SECRETO)
            .withCopyFileToContainer(
                    MountableFile.forHostPath(Path.of("..", "infra", "keycloak", "realm-biohub.json").toAbsolutePath()),
                    "/opt/keycloak/data/import/realm-biohub.json")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/realms/biohub").forPort(8080).withStartupTimeout(Duration.ofMinutes(3)));

    private final HttpClient http = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();
    private KeycloakAdminAdapter adaptador;

    private String url() {
        return "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080);
    }

    private KeycloakAdminAdapter adaptadorCon(String secreto) {
        PropiedadesIdentidad propiedades = new PropiedadesIdentidad(url(), "biohub", "biohub-gateway", secreto,
                Duration.ofMinutes(5));
        return new KeycloakAdminAdapter(RestClient.builder(), propiedades, Clock.systemUTC());
    }

    @BeforeEach
    void preparar() {
        adaptador = adaptadorCon(SECRETO);
    }

    private static ClienteIdentidad.NuevoUsuario nuevo(RolInterno rol) {
        String nombre = "u" + UUID.randomUUID().toString().substring(0, 8);
        return new ClienteIdentidad.NuevoUsuario(nombre, nombre + "@biohub.test", "Nombre", "Apellido", rol);
    }

    private HttpResponse<String> pedirToken(String usuario, String contrasena) throws Exception {
        String formulario = "grant_type=password&client_id=biohub-pruebas&username=" + usuario + "&password="
                + URLEncoder.encode(contrasena, StandardCharsets.UTF_8);
        HttpRequest peticion = HttpRequest.newBuilder(
                        URI.create(url() + "/realms/biohub/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formulario)).build();
        return http.send(peticion, HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode rolesDelToken(HttpResponse<String> respuesta) throws Exception {
        String token = json.readTree(respuesta.body()).get("access_token").asText();
        String carga = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        return json.readTree(carga).at("/realm_access/roles");
    }

    @Test
    void listaLosUsuariosDePruebaConSuRolYSinLaCuentaDeServicio() {
        ClienteIdentidad.Pagina pagina = adaptador.listar("investigador1", 0, 10);

        assertThat(pagina.total()).isEqualTo(1);
        assertThat(pagina.usuarios().get(0).roles()).containsExactly(RolInterno.INVESTIGADOR_CAMPO);

        ClienteIdentidad.Pagina todos = adaptador.listar(null, 0, 50);
        assertThat(todos.usuarios()).extracting(UsuarioInterno::usuario)
                .contains("admin1", "sinrol1")
                .doesNotContain("service-account-biohub-gateway");
    }

    @Test
    void elUsuarioSinRolNoTieneRolesDeNegocio() throws Exception {
        UsuarioInterno sinRol = adaptador.listar("sinrol1", 0, 1).usuarios().get(0);

        assertThat(sinRol.roles()).isEmpty();
        assertThat(rolesDelToken(pedirToken("sinrol1", CONTRASENA_PRUEBA)).toString())
                .doesNotContain("investigador_campo", "administrador");
    }

    @Test
    void creaUnUsuarioConExactamenteUnRolYContrasenaTemporal() {
        ClienteIdentidad.NuevoUsuario usuario = nuevo(RolInterno.AUDITOR_CIENTIFICO);

        String id = adaptador.crear(usuario, "Temporal-de-prueba-123");

        UsuarioInterno creado = adaptador.buscar(id).orElseThrow();
        assertThat(creado.usuario()).isEqualTo(usuario.usuario());
        assertThat(creado.activo()).isTrue();
        assertThat(creado.roles()).containsExactly(RolInterno.AUDITOR_CIENTIFICO);
    }

    @Test
    void unaContrasenaTemporalObligaACambiarlaAntesDeEntrar() throws Exception {
        ClienteIdentidad.NuevoUsuario usuario = nuevo(RolInterno.AUDITOR_CIENTIFICO);
        adaptador.crear(usuario, "Temporal-de-prueba-123");

        HttpResponse<String> respuesta = pedirToken(usuario.usuario(), "Temporal-de-prueba-123");

        assertThat(respuesta.statusCode()).isEqualTo(400);
        assertThat(respuesta.body()).contains("Account is not fully set up");
    }

    @Test
    void usuarioRepetidoDaConflicto() {
        ClienteIdentidad.NuevoUsuario usuario = nuevo(RolInterno.AUDITOR_CIENTIFICO);
        adaptador.crear(usuario, "Temporal-de-prueba-123");

        assertThatThrownBy(() -> adaptador.crear(usuario, "Temporal-de-prueba-123"))
                .isInstanceOf(ConflictoException.class);
    }

    @Test
    void datosQueKeycloakRechazaNoSeConfundenConUnaCaida() {
        ClienteIdentidad.NuevoUsuario base = nuevo(RolInterno.AUDITOR_CIENTIFICO);
        ClienteIdentidad.NuevoUsuario invalido = new ClienteIdentidad.NuevoUsuario(base.usuario(), base.email(),
                "<script>", "Apellido", RolInterno.AUDITOR_CIENTIFICO);

        assertThatThrownBy(() -> adaptador.crear(invalido, "Temporal-de-prueba-123"))
                .isInstanceOf(DatosRechazadosException.class);
    }

    @Test
    void asignarRolDejaExactamenteUno() {
        String id = adaptador.crear(nuevo(RolInterno.AUDITOR_CIENTIFICO), "Temporal-de-prueba-123");

        adaptador.asignarRol(id, RolInterno.CIENTIFICO_ALTO_RANGO);

        assertThat(adaptador.buscar(id).orElseThrow().roles()).containsExactly(RolInterno.CIENTIFICO_ALTO_RANGO);
    }

    @Test
    void quitarRolesDejaAlUsuarioSinRoles() {
        String id = adaptador.crear(nuevo(RolInterno.ADMINISTRADOR), "Temporal-de-prueba-123");

        adaptador.quitarRoles(id);

        assertThat(adaptador.buscar(id).orElseThrow().roles()).isEmpty();
    }

    @Test
    void unUsuarioDesactivadoNoPuedeEntrar() throws Exception {
        String id = adaptador.listar("auditor1", 0, 1).usuarios().get(0).id();
        assertThat(pedirToken("auditor1", CONTRASENA_PRUEBA).statusCode()).isEqualTo(200);

        adaptador.cambiarEstado(id, false);

        UsuarioInterno desactivado = adaptador.buscar(id).orElseThrow();
        assertThat(desactivado.activo()).isFalse();
        assertThat(desactivado.email()).isEqualTo("auditor1@biohub.test");
        assertThat(pedirToken("auditor1", CONTRASENA_PRUEBA).statusCode()).isNotEqualTo(200);

        adaptador.cambiarEstado(id, true);
        assertThat(pedirToken("auditor1", CONTRASENA_PRUEBA).statusCode()).isEqualTo(200);
    }

    @Test
    void cerrarSesionesFuncionaParaUnUsuarioExistente() {
        String id = adaptador.listar("cientifico1", 0, 1).usuarios().get(0).id();

        adaptador.cerrarSesiones(id);

        assertThat(adaptador.buscar(id)).isPresent();
    }

    @Test
    void usuarioInexistenteDaVacio() {
        assertThat(adaptador.buscar(UUID.randomUUID().toString())).isEmpty();
    }

    @Test
    void tresIntentosFallidosBloqueanLaCuenta() throws Exception {
        assertThat(pedirToken("analista1", CONTRASENA_PRUEBA).statusCode()).isEqualTo(200);

        for (int intento = 0; intento < 3; intento++) {
            assertThat(pedirToken("analista1", "equivocada-" + intento).statusCode()).isNotEqualTo(200);
            Thread.sleep(1_100); // Separados más de 1 s: si no, Keycloak bloquea antes por "intento rápido"
        }

        assertThat(pedirToken("analista1", CONTRASENA_PRUEBA).statusCode()).isNotEqualTo(200);
    }

    @Test
    void conUnSecretoEquivocadoRespondeNoDisponible() {
        assertThatThrownBy(() -> adaptadorCon("equivocado").listar(null, 0, 1))
                .isInstanceOf(IdentidadNoDisponibleException.class);
    }

    @Test
    void sinSecretoRespondeNoDisponible() {
        assertThatThrownBy(() -> adaptadorCon("").listar(null, 0, 1))
                .isInstanceOf(IdentidadNoDisponibleException.class);
    }
}
