package co.edu.unbosque.biohub.gateway.infrastructure.limite;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.gateway.domain.ResultadoLimite;

class LimitadorMemoriaTest {

    private static final Duration VENTANA = Duration.ofSeconds(60);

    private final AtomicLong ahora = new AtomicLong(1_000);
    private final LimitadorMemoria limitador = new LimitadorMemoria(ahora::get);

    @Test
    void permiteHastaElLimiteYRechazaElSiguiente() {
        assertThat(limitador.consumir("a", 2, VENTANA).restantes()).isEqualTo(1);
        assertThat(limitador.consumir("a", 2, VENTANA).permitido()).isTrue();

        ResultadoLimite tercero = limitador.consumir("a", 2, VENTANA);

        assertThat(tercero.permitido()).isFalse();
        assertThat(tercero.restantes()).isZero();
    }

    @Test
    void cadaClaveCuentaPorSeparado() {
        limitador.consumir("a", 1, VENTANA);

        assertThat(limitador.consumir("b", 1, VENTANA).permitido()).isTrue();
    }

    @Test
    void laVentanaSeReiniciaAlTerminar() {
        limitador.consumir("a", 1, VENTANA);
        ahora.addAndGet(60_000);

        assertThat(limitador.consumir("a", 1, VENTANA).permitido()).isTrue();
    }

    @Test
    void informaCuantoFaltaParaReintentar() {
        limitador.consumir("a", 1, VENTANA);
        ahora.addAndGet(45_000);

        assertThat(limitador.consumir("a", 1, VENTANA).segundosParaReintentar()).isEqualTo(15);
    }

    @Test
    void consultarNoGastaCupo() {
        limitador.consultar("a", 1, VENTANA);

        assertThat(limitador.consumir("a", 1, VENTANA).permitido()).isTrue();
    }

    @Test
    void consultarDetectaElBloqueo() {
        assertThat(limitador.consultar("a", 1, VENTANA).permitido()).isTrue();
        limitador.consumir("a", 1, VENTANA);

        assertThat(limitador.consultar("a", 1, VENTANA).permitido()).isFalse();
    }
}
