package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.repository.CrudRepository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.EstadoConservacion;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgo;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgoRepository;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.AuditoriaAccesoUbicacion;
import jakarta.persistence.EntityManager;

/** ARQ-10 (RNF9, ASR6): cada decorador de {@code RepositorioUbicaciones} se prueba por separado. */
class DecoradoresRepositorioUbicacionesTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-11-03T10:00:00Z"), ZoneOffset.UTC);
    private final UUID id = UUID.randomUUID();
    private final RepositorioUbicaciones envuelto = mock(RepositorioUbicaciones.class);
    private final CifradorCoordenadas cifrador = mock(CifradorCoordenadas.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private Logger logger;

    private UbicacionHallazgo fila() {
        return new UbicacionHallazgo(id, 1, "15", "Atelopus ebenoides", LocalDate.of(2026, 3, 14), "inv", EstadoConservacion.EN_PELIGRO);
    }

    @BeforeEach
    void capturar() {
        logger = (Logger) LoggerFactory.getLogger("biohub.auditoria.ubicacion");
        logs.start();
        logger.addAppender(logs);
    }

    @AfterEach
    void soltar() {
        logger.detachAppender(logs);
    }

    private List<String> auditoria() {
        return new ArrayList<>(logs.list).stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    @DisplayName("ARQ-10 RNF9: DecoradorCifradoCoordenadas cifra ANTES de delegar y el componente de abajo recibe null, nunca texto plano")
    void cifraAntesDeGuardar() {
        CoordenadasCifradas cifradas = new CoordenadasCifradas(new byte[32], new byte[12], "v1");
        when(cifrador.cifrar(id, new Coordenadas(5.5, -73.5))).thenReturn(cifradas);
        UbicacionHallazgo fila = fila();

        new DecoradorCifradoCoordenadas(envuelto, cifrador, RELOJ).guardar(fila, new Coordenadas(5.5, -73.5));

        InOrder orden = inOrder(cifrador, envuelto);
        orden.verify(cifrador).cifrar(id, new Coordenadas(5.5, -73.5));
        orden.verify(envuelto).guardar(fila, null);
        assertThat(fila.coordenadasCifradas()).isEqualTo(cifradas);
        assertThat(fila.getCifradoEn()).isEqualTo(Instant.parse("2026-11-03T10:00:00Z"));
    }

    @Test
    @DisplayName("ARQ-10: sin coordenadas (alta sin ubicación o actualización del índice) no se cifra pero sí se guarda")
    void sinCoordenadasNoCifra() {
        UbicacionHallazgo fila = fila();
        new DecoradorCifradoCoordenadas(envuelto, cifrador, RELOJ).guardar(fila, null);
        org.mockito.Mockito.verifyNoInteractions(cifrador);
        verify(envuelto).guardar(fila, null);
        assertThat(fila.coordenadasCifradas()).isNull();
    }

    @Test
    @DisplayName("ARQ-10 ASR6: si el cifrado falla no se guarda nada y la excepción sube (revierte la TX de registro)")
    void siElCifradoFallaNoSeGuarda() {
        when(cifrador.cifrar(any(), any())).thenThrow(new IllegalStateException("No se pudo cifrar la ubicación"));
        assertThatThrownBy(() -> new DecoradorCifradoCoordenadas(envuelto, cifrador, RELOJ).guardar(fila(), new Coordenadas(1, 2)))
                .isInstanceOf(IllegalStateException.class);
        verify(envuelto, never()).guardar(any(), any());
    }

    @Test
    @DisplayName("ARQ-10: obtenerUbicacionExacta descifra con el cifrador (solo aquí se descifra)")
    void descifraSoloEnObtenerUbicacionExacta() {
        CoordenadasCifradas cifradas = new CoordenadasCifradas(new byte[32], new byte[12], "v1");
        when(cifrador.descifrar(id, cifradas)).thenReturn(new Coordenadas(5.5, -73.5));
        assertThat(new DecoradorCifradoCoordenadas(envuelto, cifrador, RELOJ).obtenerUbicacionExacta("juana", id, cifradas))
                .isEqualTo(new Coordenadas(5.5, -73.5));
        verify(envuelto, never()).obtenerUbicacionExacta(any(), any(), any());
    }

    @Test
    @DisplayName("ARQ-10 RNF9: el componente JPA se niega a guardar coordenadas en claro si falta el decorador de cifrado")
    void jpaRechazaCoordenadasEnClaro() {
        UbicacionHallazgoRepository spring = mock(UbicacionHallazgoRepository.class);
        RepositorioUbicacionesJpa jpa = new RepositorioUbicacionesJpa(spring, mock(EntityManager.class));
        assertThatThrownBy(() -> jpa.guardar(fila(), new Coordenadas(5.5, -73.5)))
                .isInstanceOf(IllegalStateException.class).hasMessageNotContaining("5.5");
        verify(spring, never()).saveAndFlush(any());
        UbicacionHallazgo fila = fila();
        jpa.guardar(fila, null);
        verify(spring).saveAndFlush(fila);
    }

    @Test
    @DisplayName("ARQ-10 RF13: DecoradorAuditoriaAcceso registra usuario y hallazgo de cada consulta de coordenadas exactas, sin coordenadas")
    void auditaCadaAccesoExacto() {
        CoordenadasCifradas cifradas = new CoordenadasCifradas(new byte[32], new byte[12], "v1");
        when(envuelto.obtenerUbicacionExacta("juana", id, cifradas)).thenReturn(new Coordenadas(5.1234567, -73.7654321));
        Coordenadas c = new DecoradorAuditoriaAcceso(envuelto, new AuditoriaAccesoUbicacionLog())
                .obtenerUbicacionExacta("juana", id, cifradas);
        assertThat(c).isEqualTo(new Coordenadas(5.1234567, -73.7654321));
        assertThat(auditoria()).singleElement().satisfies(l -> {
            assertThat(l).contains("usuario=juana").contains("endpoint=obtenerUbicacionExacta")
                    .contains("resultado=PERMITIDO").contains("hallazgoId=" + id).contains("cantidad=1").contains("omitidos=0");
            assertThat(l).doesNotContain("5.1234567").doesNotContain("73.7654321");
        });
    }

    @Test
    @DisplayName("ARQ-10: si no se puede descifrar, la auditoría lo anota como omitido y la excepción sigue su camino")
    void auditaElAccesoFallido() {
        CoordenadasCifradas cifradas = new CoordenadasCifradas(new byte[32], new byte[12], "v1");
        when(envuelto.obtenerUbicacionExacta(any(), any(), any()))
                .thenThrow(new CoordenadasNoDescifrablesException(CoordenadasNoDescifrablesException.Motivo.INTEGRIDAD));
        assertThatThrownBy(() -> new DecoradorAuditoriaAcceso(envuelto, new AuditoriaAccesoUbicacionLog())
                .obtenerUbicacionExacta("juana", id, cifradas)).isInstanceOf(CoordenadasNoDescifrablesException.class);
        assertThat(auditoria()).singleElement().satisfies(l -> assertThat(l).contains("cantidad=0").contains("omitidos=1"));
    }

    @Test
    @DisplayName("SEC-J-10: cualquier excepcion al descifrar (no solo CoordenadasNoDescifrables) queda auditada, sin coordenadas ni mensaje")
    void auditaCualquierExcepcion() {
        CoordenadasCifradas cifradas = new CoordenadasCifradas(new byte[32], new byte[12], "v1");
        when(envuelto.obtenerUbicacionExacta(any(), any(), any()))
                .thenThrow(new IllegalStateException("falla interna lat=5.5 lon=-73.5"));
        assertThatThrownBy(() -> new DecoradorAuditoriaAcceso(envuelto, new AuditoriaAccesoUbicacionLog())
                .obtenerUbicacionExacta("juana", id, cifradas)).isInstanceOf(IllegalStateException.class);
        assertThat(auditoria()).singleElement().satisfies(l -> {
            assertThat(l).contains("usuario=juana").contains("hallazgoId=" + id).contains("cantidad=0").contains("omitidos=1");
            assertThat(l).doesNotContain("5.5").doesNotContain("73.5").doesNotContain("falla interna");
        });
    }

    @Test
    @DisplayName("ARQ-10: los decoradores delegan lo demás (existe, porId, contar, buscar) sin tocarlo")
    void delegaElResto() {
        DecoradorAuditoriaAcceso d = new DecoradorAuditoriaAcceso(envuelto, new AuditoriaAccesoUbicacionLog());
        when(envuelto.existe(id)).thenReturn(true);
        assertThat(d.existe(id)).isTrue();
        d.porId(id);
        verify(envuelto).porId(id);
        d.guardar(fila(), null);
        verify(envuelto).guardar(any(), isNull());
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("ARQ-10: ConfiguracionUbicacion arma la cadena auditoría -> cifrado -> JPA (SAD §6.3.2)")
    void laCadenaSeEnsamblaEnElOrdenDelSad() throws Exception {
        ObjectProvider<Clock> reloj = mock(ObjectProvider.class);
        when(reloj.getIfAvailable(any())).thenReturn(RELOJ);
        RepositorioUbicaciones cadena = new ConfiguracionUbicacion().repositorioUbicaciones(
                mock(UbicacionHallazgoRepository.class), mock(EntityManager.class), cifrador, new AuditoriaAccesoUbicacionLog(), reloj);
        assertThat(cadena).isInstanceOf(DecoradorAuditoriaAcceso.class);
        RepositorioUbicaciones medio = interno(cadena);
        assertThat(medio).isInstanceOf(DecoradorCifradoCoordenadas.class);
        assertThat(interno(medio)).isInstanceOf(RepositorioUbicacionesJpa.class);
    }

    private static RepositorioUbicaciones interno(RepositorioUbicaciones d) throws Exception {
        Field f = DecoradorRepositorioUbicaciones.class.getDeclaredField("envuelto");
        f.setAccessible(true);
        return (RepositorioUbicaciones) f.get(d);
    }

    @Test
    @DisplayName("ARQ-10: ni RepositorioUbicaciones ni el repositorio Spring Data exponen delete*, deleteAll ni saveAll")
    void sinOperacionesDeBorrado() {
        assertThat(CrudRepository.class.isAssignableFrom(UbicacionHallazgoRepository.class)).isFalse();
        assertThat(org.springframework.data.jpa.repository.JpaRepository.class.isAssignableFrom(UbicacionHallazgoRepository.class)).isFalse();
        List<String> nombres = new ArrayList<>();
        for (Class<?> tipo : List.of(UbicacionHallazgoRepository.class, RepositorioUbicaciones.class)) {
            Arrays.stream(tipo.getMethods()).map(Method::getName).forEach(nombres::add);
        }
        assertThat(nombres).noneMatch(n -> n.startsWith("delete") || n.startsWith("eliminar") || n.equals("saveAll")
                || n.equals("findAll") || n.equals("deleteAll") || n.equals("remove"));
    }
}
