package co.edu.unbosque.biohub.gateway.infrastructure.limite;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import co.edu.unbosque.biohub.gateway.domain.ResultadoLimite;
import co.edu.unbosque.biohub.gateway.infrastructure.InterruptorRedis;

class LimitadorConRespaldoTest {

    private static final Duration VENTANA = Duration.ofSeconds(60);
    private static final ResultadoLimite DE_REDIS = new ResultadoLimite(true, 5, 4, VENTANA);
    private static final ResultadoLimite DE_MEMORIA = new ResultadoLimite(true, 5, 3, VENTANA);

    private final LimitadorRedis redis = mock(LimitadorRedis.class);
    private final LimitadorMemoria memoria = mock(LimitadorMemoria.class);
    private final LimitadorConRespaldo limitador = new LimitadorConRespaldo(redis, memoria, new InterruptorRedis());

    @Test
    void conRedisSanoNoUsaLaMemoria() {
        given(redis.consumir("a", 5, VENTANA)).willReturn(DE_REDIS);

        assertThat(limitador.consumir("a", 5, VENTANA)).isEqualTo(DE_REDIS);
    }

    @Test
    void siRedisFallaSigueLimitandoEnMemoria() {
        given(redis.consumir(anyString(), anyInt(), any())).willThrow(new RedisConnectionFailureException("caído"));
        given(memoria.consumir("a", 5, VENTANA)).willReturn(DE_MEMORIA);

        assertThat(limitador.consumir("a", 5, VENTANA)).isEqualTo(DE_MEMORIA);
    }

    @Test
    void trasUnFalloNoInsisteEnRedisDuranteUnosSegundos() {
        given(redis.consumir(anyString(), anyInt(), any())).willThrow(new RedisConnectionFailureException("caído"));
        given(memoria.consumir("a", 5, VENTANA)).willReturn(DE_MEMORIA);

        limitador.consumir("a", 5, VENTANA);
        limitador.consumir("a", 5, VENTANA);
        limitador.consumir("a", 5, VENTANA);

        verify(redis, times(1)).consumir("a", 5, VENTANA);
        verify(memoria, times(3)).consumir("a", 5, VENTANA);
    }

    @Test
    void consultarTambienTieneRespaldo() {
        given(redis.consultar(anyString(), anyInt(), any())).willThrow(new RedisConnectionFailureException("caído"));
        given(memoria.consultar("a", 5, VENTANA)).willReturn(DE_MEMORIA);

        assertThat(limitador.consultar("a", 5, VENTANA)).isEqualTo(DE_MEMORIA);
    }
}
