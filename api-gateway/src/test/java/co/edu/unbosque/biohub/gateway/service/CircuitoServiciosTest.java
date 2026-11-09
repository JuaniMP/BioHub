package co.edu.unbosque.biohub.gateway.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.gateway.config.PropiedadesCircuito;

class CircuitoServiciosTest {

    private static final String SERVICIO = "http://equipos:8082";

    private final AtomicLong ahora = new AtomicLong(1_000);
    private final CircuitoServicios circuito =
            new CircuitoServicios(new PropiedadesCircuito(3, Duration.ofSeconds(10)), ahora::get);

    private void fallar(int veces) {
        for (int i = 0; i < veces; i++) {
            circuito.permitir(SERVICIO);
            circuito.registrarFallo(SERVICIO);
        }
    }

    @Test
    void cerradoDejaPasar() {
        assertThatCode(() -> circuito.permitir(SERVICIO)).doesNotThrowAnyException();
    }

    @Test
    void seAbreTrasLosFallosSeguidosEIndicaCuandoReintentar() {
        fallar(3);

        assertThatThrownBy(() -> circuito.permitir(SERVICIO))
                .isInstanceOfSatisfying(CircuitoAbiertoException.class,
                        e -> org.assertj.core.api.Assertions.assertThat(e.segundosParaReintentar()).isEqualTo(10));
    }

    @Test
    void unExitoReiniciaLaCuentaDeFallos() {
        fallar(2);
        circuito.permitir(SERVICIO);
        circuito.registrarExito(SERVICIO);
        fallar(2);

        assertThatCode(() -> circuito.permitir(SERVICIO)).doesNotThrowAnyException();
    }

    @Test
    void cadaServicioTieneSuPropioCircuito() {
        fallar(3);

        assertThatCode(() -> circuito.permitir("http://reportes:8084")).doesNotThrowAnyException();
    }

    @Test
    void trasElTiempoAbiertoDejaPasarSoloUnaPrueba() {
        fallar(3);
        ahora.addAndGet(10_000);

        assertThatCode(() -> circuito.permitir(SERVICIO)).doesNotThrowAnyException();
        assertThatThrownBy(() -> circuito.permitir(SERVICIO)).isInstanceOf(CircuitoAbiertoException.class);
    }

    @Test
    void siLaPruebaSaleBienSeCierra() {
        fallar(3);
        ahora.addAndGet(10_000);
        circuito.permitir(SERVICIO);
        circuito.registrarExito(SERVICIO);

        assertThatCode(() -> circuito.permitir(SERVICIO)).doesNotThrowAnyException();
        assertThatCode(() -> circuito.permitir(SERVICIO)).doesNotThrowAnyException();
    }

    @Test
    void siLaPruebaFallaSeVuelveAAbrirDeInmediato() {
        fallar(3);
        ahora.addAndGet(10_000);
        circuito.permitir(SERVICIO);
        circuito.registrarFallo(SERVICIO);

        assertThatThrownBy(() -> circuito.permitir(SERVICIO)).isInstanceOf(CircuitoAbiertoException.class);
    }

    @Test
    void unaPruebaLiberadaSinResultadoPermiteOtra() {
        fallar(3);
        ahora.addAndGet(10_000);
        circuito.permitir(SERVICIO);
        circuito.liberar(SERVICIO);

        assertThatCode(() -> circuito.permitir(SERVICIO)).doesNotThrowAnyException();
    }
}
