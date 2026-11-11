package co.edu.unbosque.biohub.gateway.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import co.edu.unbosque.biohub.gateway.domain.AlmacenCredenciales;
import co.edu.unbosque.biohub.gateway.domain.ApiKey;
import co.edu.unbosque.biohub.gateway.domain.ApiKeyRepository;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorAutenticado;
import co.edu.unbosque.biohub.gateway.domain.ConsumidorExternoRepository;
import co.edu.unbosque.biohub.gateway.domain.CredencialApiKey;
import co.edu.unbosque.biohub.gateway.domain.EstadoApiKey;

class ServicioApiKeysTest {

    private static final Instant AHORA = Instant.parse("2026-11-05T12:00:00Z");
    private static final UUID CONSUMIDOR = UUID.randomUUID();
    private static final UUID KEY_ID = UUID.randomUUID();

    private final GeneradorApiKey generador = new GeneradorApiKey();
    private final ApiKeyRepository keys = mock(ApiKeyRepository.class);
    private final ConsumidorExternoRepository consumidores = mock(ConsumidorExternoRepository.class);
    private final AlmacenCredenciales credenciales = mock(AlmacenCredenciales.class);
    private final RegistroUltimoUso ultimoUso = mock(RegistroUltimoUso.class);
    private ServicioApiKeys servicio;
    private GeneradorApiKey.KeyGenerada generada;

    @BeforeEach
    void preparar() {
        servicio = new ServicioApiKeys(generador, keys, consumidores, credenciales, ultimoUso,
                Clock.fixed(AHORA, ZoneOffset.UTC));
        generada = generador.generar();
    }

    private void credencialGuardada(EstadoApiKey estado, Instant expiraEn, Integer limite) {
        given(credenciales.porPrefijo(generada.prefijo())).willReturn(Optional.of(
                new CredencialApiKey(KEY_ID, CONSUMIDOR, generada.hash(), estado, expiraEn, limite)));
    }

    @Test
    void keyActivaEsValidaYRegistraElUso() {
        credencialGuardada(EstadoApiKey.ACTIVA, null, 200);

        Optional<ConsumidorAutenticado> resultado = servicio.validar(generada.key());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().consumidorId()).isEqualTo(CONSUMIDOR);
        assertThat(resultado.get().limitePorMinuto()).isEqualTo(200);
        verify(ultimoUso).marcar(KEY_ID, AHORA);
    }

    @Test
    void keyConFormatoInvalidoNoConsultaNada() {
        assertThat(servicio.validar("inventada")).isEmpty();
        assertThat(servicio.validar(null)).isEmpty();
        verifyNoInteractions(credenciales);
    }

    @Test
    void keyDesconocidaSeRechaza() {
        given(credenciales.porPrefijo(generada.prefijo())).willReturn(Optional.empty());

        assertThat(servicio.validar(generada.key())).isEmpty();
    }

    @Test
    void keyRevocadaSeRechaza() {
        credencialGuardada(EstadoApiKey.REVOCADA, null, null);

        assertThat(servicio.validar(generada.key())).isEmpty();
        verify(ultimoUso, never()).marcar(any(), any());
    }

    @Test
    void keyVencidaSeRechaza() {
        credencialGuardada(EstadoApiKey.ACTIVA, AHORA.minusSeconds(1), null);

        assertThat(servicio.validar(generada.key())).isEmpty();
    }

    @Test
    void keyConVencimientoFuturoEsValida() {
        credencialGuardada(EstadoApiKey.ACTIVA, AHORA.plusSeconds(1), null);

        assertThat(servicio.validar(generada.key())).isPresent();
    }

    @Test
    void mismoPrefijoConSecretoDistintoSeRechaza() {
        credencialGuardada(EstadoApiKey.ACTIVA, null, null);
        String falsificada = generada.prefijo() + "_" + "A".repeat(43);

        assertThat(servicio.validar(falsificada)).isEmpty();
    }

    @Test
    void emitirGuardaElHashYNuncaLaKeyEnClaro() {
        given(consumidores.existsById(CONSUMIDOR)).willReturn(true);
        given(keys.save(any(ApiKey.class))).willAnswer(llamada -> llamada.getArgument(0));

        ServicioApiKeys.KeyEmitida emitida = servicio.emitir(CONSUMIDOR, null, "sub-admin");

        ArgumentCaptor<ApiKey> guardada = ArgumentCaptor.forClass(ApiKey.class);
        verify(keys).save(guardada.capture());
        assertThat(generador.prefijoDe(emitida.key())).contains(guardada.getValue().getPrefijo());
        assertThat(guardada.getValue().toString()).doesNotContain(emitida.key());
        assertThat(emitida.toString()).doesNotContain(emitida.key());
    }

    @Test
    void emitirParaConsumidorInexistenteDa404() {
        given(consumidores.existsById(CONSUMIDOR)).willReturn(false);

        assertThatThrownBy(() -> servicio.emitir(CONSUMIDOR, null, "sub-admin"))
                .isInstanceOf(RecursoNoEncontradoException.class);
        verify(keys, never()).save(any());
    }

    @Test
    void revocarGuardaYDespuesInvalidaLaCache() {
        ApiKey key = new ApiKey(KEY_ID, CONSUMIDOR, generada.prefijo(), generada.hash(), null, "sub-admin", AHORA);
        given(keys.findById(KEY_ID)).willReturn(Optional.of(key));

        servicio.revocar(KEY_ID, "sub-otro");

        assertThat(key.getEstado()).isEqualTo(EstadoApiKey.REVOCADA);
        assertThat(key.getRevocadaEn()).isEqualTo(AHORA);
        InOrder orden = inOrder(keys, credenciales);
        orden.verify(keys).save(key);
        orden.verify(credenciales).invalidar(generada.prefijo());
    }

    @Test
    void revocarDosVecesConservaLaPrimeraRevocacion() {
        ApiKey key = new ApiKey(KEY_ID, CONSUMIDOR, generada.prefijo(), generada.hash(), null, "sub-admin", AHORA);
        key.revocar("primero", AHORA.minusSeconds(60));
        given(keys.findById(KEY_ID)).willReturn(Optional.of(key));

        servicio.revocar(KEY_ID, "segundo");

        assertThat(key.getRevocadaEn()).isEqualTo(AHORA.minusSeconds(60));
    }

    @Test
    void revocarKeyInexistenteDa404() {
        given(keys.findById(KEY_ID)).willReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.revocar(KEY_ID, "sub-admin"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
