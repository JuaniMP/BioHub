package co.edu.unbosque.biohub.cientifico.ubicacion.qa;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MySQLContainer;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;

import co.edu.unbosque.biohub.cientifico.ubicacion.AppPruebaUbicacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.RegistroUbicacionComando;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.UbicacionService;

/**
 * Base de las pruebas QA de ubicación. A diferencia de las del desarrollador, NO simula el JwtDecoder:
 * valida JWT firmados con RSA contra un JWKS servido en memoria por un HttpServer local, con las mismas
 * reglas de producción (firma, emisor, vigencia, audiencia). MySQL 8.4 efímero (Testcontainers).
 */
@SpringBootTest(classes = AppPruebaUbicacion.class)
@AutoConfigureMockMvc
public abstract class QaBase {

    public static final String ISSUER = "http://qa-keycloak.invalid/realms/biohub";
    public static final String AUDIENCIA = "biohub-api";
    public static final double[] CENTINELA_1 = {4.6097, -74.0817};
    public static final double[] CENTINELA_2 = {5.1234567, -73.1234567};

    protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cientifico").withUsername("test").withPassword("test");
    protected static final String K1 = llave();
    protected static final String K2 = llave();
    protected static final RSAKey CLAVE_FIRMA;
    protected static final RSAKey CLAVE_AJENA;
    private static final HttpServer JWKS;

    static {
        MYSQL.start();
        try {
            CLAVE_FIRMA = nuevaClaveRsa("qa-kid-1");
            CLAVE_AJENA = nuevaClaveRsa("qa-kid-1"); // mismo kid, otra clave: la firma no debe validar
            String jwks = new JWKSet(CLAVE_FIRMA.toPublicJWK()).toString();
            JWKS = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            JWKS.createContext("/jwks", ex -> {
                byte[] b = jwks.getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.sendResponseHeaders(200, b.length);
                ex.getResponseBody().write(b);
                ex.close();
            });
            JWKS.start();
        } catch (IOException | JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    private static RSAKey nuevaClaveRsa(String kid) throws JOSEException {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            KeyPair p = g.generateKeyPair();
            return new RSAKey.Builder((RSAPublicKey) p.getPublic()).privateKey((RSAPrivateKey) p.getPrivate())
                    .keyID(kid).build();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new JOSEException(e.getMessage());
        }
    }

    private static String llave() {
        byte[] b = new byte[32];
        new java.security.SecureRandom().nextBytes(b);
        return java.util.Base64.getEncoder().encodeToString(b);
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("biohub.ubicacion.cifrado.claves", () -> "v1:" + K1 + ",v2:" + K2);
        r.add("biohub.ubicacion.cifrado.clave-activa", () -> "v1");
        r.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
        r.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCIA);
    }

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected TransactionTemplate tx;
    @Autowired
    protected UbicacionService ubicacion;

    protected static final List<String> ROLES_INTERNOS = List.of(
            "investigador_campo", "auditor_cientifico", "cientifico_alto_rango", "analista_ambiental", "administrador");

    // ---- JWT -----------------------------------------------------------------------------------------------

    /** Opciones de un token de prueba. */
    public record Tok(String iss, Object aud, Instant exp, RSAKey firma, JWSAlgorithm alg, String usuario,
                      List<String> roles) {
        public static Tok valido(String usuario, String... roles) {
            return new Tok(ISSUER, AUDIENCIA, Instant.now().plusSeconds(300), CLAVE_FIRMA, JWSAlgorithm.RS256,
                    usuario, List.of(roles));
        }
        public Tok iss(String v) { return new Tok(v, aud, exp, firma, alg, usuario, roles); }
        public Tok aud(Object v) { return new Tok(iss, v, exp, firma, alg, usuario, roles); }
        public Tok exp(Instant v) { return new Tok(iss, aud, v, firma, alg, usuario, roles); }
        public Tok firma(RSAKey v) { return new Tok(iss, aud, exp, v, alg, usuario, roles); }
    }

    protected static String jwt(Tok t) {
        try {
            JWTClaimsSet.Builder c = new JWTClaimsSet.Builder().issuer(t.iss()).subject("sub-" + t.usuario())
                    .issueTime(Date.from(Instant.now().minusSeconds(5))).expirationTime(Date.from(t.exp()))
                    .claim("preferred_username", t.usuario())
                    .claim("realm_access", Map.of("roles", t.roles()));
            if (t.aud() != null) {
                c.claim("aud", t.aud());
            }
            SignedJWT s = new SignedJWT(new JWSHeader.Builder(t.alg()).keyID("qa-kid-1").build(), c.build());
            s.sign(new RSASSASigner(t.firma()));
            return s.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException(e);
        }
    }

    protected static String bearer(String usuario, String... roles) {
        return "Bearer " + jwt(Tok.valido(usuario, roles));
    }

    /** JWT con alg=none (sin firma). */
    protected static String jwtAlgNone() {
        return "Bearer " + new PlainJWT(new JWTClaimsSet.Builder().issuer(ISSUER).audience(AUDIENCIA)
                .expirationTime(Date.from(Instant.now().plusSeconds(300))).claim("preferred_username", "atacante")
                .claim("realm_access", Map.of("roles", List.of("cientifico_alto_rango", "administrador"))).build())
                .serialize();
    }

    /** Token válido con el payload reemplazado (la firma ya no corresponde). */
    protected static String jwtPayloadAlterado(String usuario, String rolOriginal, String rolNuevo) {
        String t = jwt(Tok.valido(usuario, rolOriginal));
        String[] p = t.split("\\.");
        String payload = new String(java.util.Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8)
                .replace(rolOriginal, rolNuevo);
        return "Bearer " + p[0] + "." + java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "." + p[2];
    }

    // ---- datos ---------------------------------------------------------------------------------------------

    protected void limpiar() {
        jdbc.update("DELETE FROM ubicacion_hallazgo");
    }

    protected UUID sembrar(String nombre, LocalDate fecha, String investigador, String departamento,
                           EstadoConservacion estado, Double lat, Double lon) {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(s -> ubicacion.registrar(
                new RegistroUbicacionComando(id, 1, nombre, fecha, investigador, departamento, estado, lat, lon)));
        return id;
    }
}
