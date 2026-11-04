package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

import co.edu.unbosque.biohub.cientifico.ubicacion.BaseUbicacionMySqlTest;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.vista.RecifradoResponse;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgoRepository;
import co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure.CifradorAesGcm;
import co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure.ProveedorDeLlavesEntorno;
import jakarta.persistence.EntityManager;

/** Rotación de llave (v1 -> v2) y re-cifrado. La llave "activa" del contexto es v1; aquí se simula v2. */
class RecifradoServiceTest extends BaseUbicacionMySqlTest {

    @Autowired
    private EntityManager em;
    @Autowired
    private PlatformTransactionManager tm;
    @Autowired
    private AuditoriaAccesoUbicacion auditoria;
    @Autowired
    private ApplicationContext ctx;
    @Autowired
    private UbicacionHallazgoRepository repositorio;

    private final ProveedorDeLlavesEntorno rotada = new ProveedorDeLlavesEntorno("v1:" + K1 + ",v2:" + K2, "v2");
    private final ProveedorDeLlavesEntorno soloV2 = new ProveedorDeLlavesEntorno("v2:" + K2, "v2");

    private RecifradoService servicio(CifradorCoordenadas cifrador, ProveedorDeLlavesEntorno proveedor) {
        return new RecifradoService(em, cifrador, proveedor, tm, auditoria, ctx.getBeanProvider(java.time.Clock.class));
    }

    private UUID alta(String nombre, double lat, double lon) {
        return sembrar(nombre, LocalDate.of(2026, 3, 14), "inv", "15", EstadoConservacion.EN_PELIGRO, lat, lon);
    }

    @Test
    void rotacionRecifraTodoConNonceNuevoSinPerderLasCoordenadas() {
        UUID a = alta("Atelopus a", 5.1, -73.1);
        UUID b = alta("Atelopus b", 6.2, -74.2);
        sembrar("Sin coords", LocalDate.of(2026, 1, 1), "inv", "15", EstadoConservacion.SIN_EVALUAR, null, null);
        CoordenadasCifradas antesA = repositorio.findById(a).orElseThrow().coordenadasCifradas();
        em.clear();
        assertThat(antesA.claveVersion()).isEqualTo("v1");

        RecifradoResponse r = servicio(new CifradorAesGcm(rotada), rotada).recifrar("admin");

        assertThat(r.claveActiva()).isEqualTo("v2");
        assertThat(r.revisados()).isEqualTo(2);
        assertThat(r.recifrados()).isEqualTo(2);
        assertThat(r.fallidos()).isZero();
        assertThat(r.pendientesPorVersion()).isEmpty();
        assertThat(jdbc.queryForList("SELECT DISTINCT clave_version FROM ubicacion_hallazgo WHERE coord_cifrada IS NOT NULL", String.class))
                .containsExactly("v2");
        em.clear();
        CoordenadasCifradas despuesA = repositorio.findById(a).orElseThrow().coordenadasCifradas();
        assertThat(despuesA.nonce()).isNotEqualTo(antesA.nonce());
        assertThat(despuesA.textoCifrado()).isNotEqualTo(antesA.textoCifrado());
        // con v1 ya retirada, todo se sigue leyendo y vale lo mismo
        CifradorAesGcm soloLaNueva = new CifradorAesGcm(soloV2);
        assertThat(soloLaNueva.descifrar(a, despuesA)).isEqualTo(new Coordenadas(5.1, -73.1));
        assertThat(soloLaNueva.descifrar(b, repositorio.findById(b).orElseThrow().coordenadasCifradas()))
                .isEqualTo(new Coordenadas(6.2, -74.2));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo WHERE cifrado_en IS NULL AND coord_cifrada IS NOT NULL", Integer.class)).isZero();
    }

    @Test
    void esIdempotenteYReanudable() {
        alta("Atelopus a", 5.1, -73.1);
        RecifradoService s = servicio(new CifradorAesGcm(rotada), rotada);
        assertThat(s.recifrar("admin").recifrados()).isEqualTo(1);
        RecifradoResponse segunda = s.recifrar("admin");
        assertThat(segunda.revisados()).isZero();
        assertThat(segunda.recifrados()).isZero();
        assertThat(segunda.pendientesPorVersion()).isEmpty();
    }

    @Test
    void procesaVariosLotesDe500() {
        tx.executeWithoutResult(s -> {
            for (int i = 0; i < 1205; i++) {
                ubicacion.registrar(new RegistroUbicacionComando(UUID.randomUUID(), 1, "Atelopus n" + i,
                        LocalDate.of(2026, 1, 1), "inv", "15", EstadoConservacion.EN_PELIGRO, 1.0, -70.0));
            }
        });
        RecifradoResponse r = servicio(new CifradorAesGcm(rotada), rotada).recifrar("admin");
        assertThat(r.revisados()).isEqualTo(1205);
        assertThat(r.recifrados()).isEqualTo(1205);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ubicacion_hallazgo WHERE clave_version = 'v2'", Integer.class)).isEqualTo(1205);
    }

    @Test
    void lasFilasIlegiblesQuedanPendientesYSeCuentanComoFallidas() {
        UUID ok = alta("Atelopus ok", 5.1, -73.1);
        alta("Atelopus retirada", 6.1, -73.1);
        alta("Atelopus alterada", 7.1, -73.1);
        jdbc.update("UPDATE ubicacion_hallazgo SET clave_version = 'v9' WHERE nombre_cientifico = 'Atelopus retirada'");
        jdbc.update("UPDATE ubicacion_hallazgo SET coord_cifrada = CONCAT(UNHEX('FF'), SUBSTRING(coord_cifrada, 2)) WHERE nombre_cientifico = 'Atelopus alterada'");

        RecifradoResponse r = servicio(new CifradorAesGcm(rotada), rotada).recifrar("admin");

        assertThat(r.revisados()).isEqualTo(3);
        assertThat(r.recifrados()).isEqualTo(1);
        assertThat(r.fallidos()).isEqualTo(2);
        assertThat(r.pendientesPorVersion()).isEqualTo(Map.of("v1", 1L, "v9", 1L));
        assertThat(jdbc.queryForObject("SELECT clave_version FROM ubicacion_hallazgo WHERE nombre_cientifico = 'Atelopus ok'", String.class)).isEqualTo("v2");
        assertThat(ok).isNotNull();
    }

    @Test
    void unSegundoRecifradoSimultaneoDa409() throws Exception {
        alta("Atelopus a", 5.1, -73.1);
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        CifradorAesGcm real = new CifradorAesGcm(rotada);
        CifradorCoordenadas bloqueante = new CifradorCoordenadas() {
            public CoordenadasCifradas cifrar(UUID id, Coordenadas c) {
                return real.cifrar(id, c);
            }

            public Coordenadas descifrar(UUID id, CoordenadasCifradas c) {
                return real.descifrar(id, c);
            }

            public CoordenadasCifradas recifrar(UUID id, CoordenadasCifradas c) {
                dentro.countDown();
                try {
                    soltar.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return real.recifrar(id, c);
            }
        };
        RecifradoService s = servicio(bloqueante, rotada);
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<RecifradoResponse> primero = pool.submit(() -> s.recifrar("admin"));
            assertThat(dentro.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> s.recifrar("otro")).isInstanceOf(RecifradoEnCursoException.class);
            soltar.countDown();
            assertThat(primero.get(10, TimeUnit.SECONDS).recifrados()).isEqualTo(1);
            // al terminar se libera: se puede volver a lanzar
            assertThat(s.recifrar("admin").revisados()).isZero();
        } finally {
            soltar.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void sinNadaQueRecifrar() {
        assertThat(servicio(new CifradorAesGcm(rotada), rotada).recifrar("admin"))
                .isEqualTo(new RecifradoResponse("v2", 0, 0, 0, Map.of()));
        assertThat(List.of()).isEmpty();
    }
}
