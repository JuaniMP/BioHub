package co.edu.unbosque.biohub.gateway.infrastructure.identidad;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import co.edu.unbosque.biohub.gateway.config.PropiedadesIdentidad;
import co.edu.unbosque.biohub.gateway.domain.ClienteIdentidad;
import co.edu.unbosque.biohub.gateway.domain.RolInterno;
import co.edu.unbosque.biohub.gateway.domain.UsuarioInterno;
import co.edu.unbosque.biohub.gateway.service.ConflictoException;
import co.edu.unbosque.biohub.gateway.service.DatosRechazadosException;
import co.edu.unbosque.biohub.gateway.service.IdentidadNoDisponibleException;
import co.edu.unbosque.biohub.gateway.service.RecursoNoEncontradoException;

/**
 * Adapter: traduce el puerto ClienteIdentidad a la API REST de administración de Keycloak. Entra con la
 * cuenta de servicio del cliente {@code biohub-gateway} (client credentials). Usa RestClient, igual
 * que el proxy, en vez de la librería de Keycloak, que traería muchas dependencias para pocas llamadas.
 */
@Component
public class KeycloakAdminAdapter implements ClienteIdentidad {

    private static final Duration TIMEOUT_CONEXION = Duration.ofSeconds(2);
    private static final Duration TIMEOUT_LECTURA = Duration.ofSeconds(5);
    private static final Duration MARGEN_TOKEN = Duration.ofSeconds(10);
    private static final ParameterizedTypeReference<List<UsuarioKeycloak>> LISTA_USUARIOS =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<List<RolKeycloak>> LISTA_ROLES =
            new ParameterizedTypeReference<>() { };

    @JsonIgnoreProperties(ignoreUnknown = true)
    record UsuarioKeycloak(String id, String username, String email, String firstName, String lastName,
                           Boolean enabled) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RolKeycloak(String id, String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record TokenKeycloak(@com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
                         @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiraEnSegundos) {
    }

    private record TokenVigente(String valor, Instant vence) {
    }

    private final RestClient cliente;
    private final PropiedadesIdentidad propiedades;
    private final Clock reloj;
    private TokenVigente token;

    public KeycloakAdminAdapter(RestClient.Builder constructor, PropiedadesIdentidad propiedades, Clock reloj) {
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(TIMEOUT_CONEXION).build());
        fabrica.setReadTimeout(TIMEOUT_LECTURA);
        this.cliente = constructor.clone().requestFactory(fabrica).build();
        this.propiedades = propiedades;
        this.reloj = reloj;
    }

    @Override
    public Pagina listar(String busqueda, int pagina, int tamano) {
        return ejecutar(() -> {
            String usuarios = admin("/users") + "?first={first}&max={max}&briefRepresentation=true";
            String conteo = admin("/users/count");
            boolean conFiltro = StringUtils.hasText(busqueda);
            List<UsuarioKeycloak> encontrados = conToken(cliente.get()
                    .uri(conFiltro ? usuarios + "&search={search}" : usuarios, parametros(pagina, tamano, busqueda)))
                    .retrieve().body(LISTA_USUARIOS);
            Long total = conToken(cliente.get()
                    .uri(conFiltro ? conteo + "?search={search}" : conteo, Map.of("search", valor(busqueda))))
                    .retrieve().body(Long.class);
            List<UsuarioInterno> resultado = encontrados.stream().map(this::conRoles).toList();
            return new Pagina(resultado, total == null ? resultado.size() : total);
        });
    }

    @Override
    public Optional<UsuarioInterno> buscar(String id) {
        return ejecutar(() -> {
            try {
                UsuarioKeycloak usuario = conToken(cliente.get().uri(admin("/users/{id}"), id))
                        .retrieve().body(UsuarioKeycloak.class);
                return Optional.of(conRoles(usuario));
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                    return Optional.empty();
                }
                throw e;
            }
        });
    }

    @Override
    public String crear(NuevoUsuario usuario, String contrasenaTemporal) {
        return ejecutar(() -> {
            RolKeycloak rol = rol(usuario.rol());
            Map<String, Object> cuerpo = Map.of(
                    "username", usuario.usuario(),
                    "email", usuario.email(),
                    "firstName", usuario.nombre(),
                    "lastName", usuario.apellido(),
                    "enabled", true,
                    "emailVerified", true,
                    "requiredActions", List.of("UPDATE_PASSWORD"),
                    "credentials", List.of(Map.of(
                            "type", "password", "value", contrasenaTemporal, "temporary", true)));
            URI ubicacion;
            try {
                ubicacion = conToken(cliente.post().uri(admin("/users")))
                        .contentType(MediaType.APPLICATION_JSON).body(cuerpo)
                        .retrieve().toBodilessEntity().getHeaders().getLocation();
            } catch (RestClientResponseException e) {
                if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                    throw new ConflictoException("Ya existe un usuario con ese nombre o correo.");
                }
                if (e.getStatusCode().isSameCodeAs(HttpStatus.BAD_REQUEST)) {
                    throw new DatosRechazadosException("Keycloak rechazó los datos del usuario.");
                }
                throw e;
            }
            String id = ubicacion.getPath().substring(ubicacion.getPath().lastIndexOf('/') + 1);
            try {
                agregarRoles(id, List.of(rol));
            } catch (RuntimeException e) {
                // HU-10 c.1: no puede quedar un usuario nuevo sin rol.
                conToken(cliente.delete().uri(admin("/users/{id}"), id)).retrieve().toBodilessEntity();
                throw e;
            }
            return id;
        });
    }

    @Override
    public void asignarRol(String id, RolInterno rol) {
        ejecutar(() -> {
            RolKeycloak nuevo = rol(rol);
            // Primero quita y luego agrega: si algo falla en medio, el usuario queda sin rol y sin acceso,
            // nunca con dos roles.
            quitarRolesDelCatalogo(id);
            agregarRoles(id, List.of(nuevo));
            return null;
        });
    }

    @Override
    public void quitarRoles(String id) {
        ejecutar(() -> {
            quitarRolesDelCatalogo(id);
            return null;
        });
    }

    @Override
    public void cambiarEstado(String id, boolean activo) {
        ejecutar(() -> conToken(cliente.put().uri(admin("/users/{id}"), id))
                .contentType(MediaType.APPLICATION_JSON).body(Map.of("enabled", activo))
                .retrieve().toBodilessEntity());
    }

    @Override
    public void cerrarSesiones(String id) {
        ejecutar(() -> conToken(cliente.post().uri(admin("/users/{id}/logout"), id))
                .retrieve().toBodilessEntity());
    }

    private UsuarioInterno conRoles(UsuarioKeycloak usuario) {
        List<RolInterno> roles = rolesDe(usuario.id()).stream()
                .map(rol -> RolInterno.buscar(rol.name()))
                .flatMap(Optional::stream)
                .toList();
        return new UsuarioInterno(usuario.id(), usuario.username(), usuario.email(), usuario.firstName(),
                usuario.lastName(), Boolean.TRUE.equals(usuario.enabled()), roles);
    }

    private List<RolKeycloak> rolesDe(String id) {
        return conToken(cliente.get().uri(admin("/users/{id}/role-mappings/realm"), id))
                .retrieve().body(LISTA_ROLES);
    }

    private RolKeycloak rol(RolInterno rol) {
        return conToken(cliente.get().uri(admin("/roles/{nombre}"), rol.nombre()))
                .retrieve().body(RolKeycloak.class);
    }

    private void agregarRoles(String id, List<RolKeycloak> roles) {
        conToken(cliente.post().uri(admin("/users/{id}/role-mappings/realm"), id))
                .contentType(MediaType.APPLICATION_JSON).body(roles)
                .retrieve().toBodilessEntity();
    }

    /** Solo toca los roles de BioHub: los roles técnicos de Keycloak no se modifican. */
    private void quitarRolesDelCatalogo(String id) {
        List<RolKeycloak> propios = rolesDe(id).stream()
                .filter(rol -> RolInterno.buscar(rol.name()).isPresent())
                .toList();
        if (!propios.isEmpty()) {
            conToken(cliente.method(org.springframework.http.HttpMethod.DELETE)
                    .uri(admin("/users/{id}/role-mappings/realm"), id))
                    .contentType(MediaType.APPLICATION_JSON).body(propios)
                    .retrieve().toBodilessEntity();
        }
    }

    private String admin(String ruta) {
        return propiedades.keycloakUrl() + "/admin/realms/" + propiedades.realm() + ruta;
    }

    private static Map<String, Object> parametros(int pagina, int tamano, String busqueda) {
        return Map.of("first", pagina * tamano, "max", tamano, "search", valor(busqueda));
    }

    private static String valor(String texto) {
        return texto == null ? "" : texto;
    }

    @SuppressWarnings("unchecked")
    private <S extends RestClient.RequestHeadersSpec<?>> S conToken(S peticion) {
        return (S) peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeServicio());
    }

    private synchronized String tokenDeServicio() {
        Instant ahora = reloj.instant();
        if (token != null && token.vence().isAfter(ahora)) {
            return token.valor();
        }
        if (!propiedades.configurada()) {
            throw new IdentidadNoDisponibleException("Falta KEYCLOAK_GATEWAY_CLIENT_SECRET");
        }
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("grant_type", "client_credentials");
        formulario.add("client_id", propiedades.clientId());
        formulario.add("client_secret", propiedades.clientSecret());
        TokenKeycloak emitido = cliente.post()
                .uri(propiedades.keycloakUrl() + "/realms/" + propiedades.realm() + "/protocol/openid-connect/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(formulario)
                .retrieve().body(TokenKeycloak.class);
        token = new TokenVigente(emitido.accessToken(),
                ahora.plusSeconds(emitido.expiraEnSegundos()).minus(MARGEN_TOKEN));
        return token.valor();
    }

    private synchronized void olvidarToken() {
        token = null;
    }

    /** Convierte los errores de comunicación en una sola excepción del dominio. */
    private <T> T ejecutar(Supplier<T> operacion) {
        try {
            return operacion.get();
        } catch (ResourceAccessException e) {
            throw new IdentidadNoDisponibleException("Keycloak no responde", e);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new RecursoNoEncontradoException("El usuario no existe.");
            }
            if (e.getStatusCode().isSameCodeAs(HttpStatus.UNAUTHORIZED)) {
                olvidarToken();
            }
            throw new IdentidadNoDisponibleException("Keycloak respondió " + e.getStatusCode().value(), e);
        }
    }
}
