package co.edu.unbosque.biohub.consultapublica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** ARQ-06 (ASR1, RNF1, HU-06.4): el monitor decide la degradación con histéresis y el forzado manual manda. */
class MonitorCargaTest {

    /** Reloj manual para controlar la ventana, la permanencia y el intervalo de evaluación. */
    static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-11-03T12:00:00Z");

        void avanzar(Duration d) {
            ahora = ahora.plus(d);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }

    private final RelojManual reloj = new RelojManual();

    /** Entrada: 10 en curso o p95 ≥ 1000 ms; salida: ≤ 4 en curso y p95 ≤ 500 ms; ventana 10 s; permanencia 15 s. */
    private static UmbralesDegradacion umbrales() {
        return new UmbralesDegradacion(true, 10, 4, 1000, 500, Duration.ofSeconds(10), 20, Duration.ofSeconds(15), Duration.ofSeconds(1));
    }

    private MonitorCarga monitor(boolean forzado, UmbralesDegradacion d) {
        return new MonitorCarga(d, forzado, reloj);
    }

    private void peticiones(MonitorCarga m, int cuantas, long duracionMs) {
        for (int i = 0; i < cuantas; i++) {
            m.iniciarPeticion();
            m.terminarPeticion(duracionMs);
        }
    }

    @Test
    @DisplayName("ARQ-06: el percentil 95 usa el rango más cercano (y 0 sin muestras)")
    void percentil95() {
        assertThat(MonitorCarga.percentil95(new long[0])).isZero();
        assertThat(MonitorCarga.percentil95(new long[] {7})).isEqualTo(7);
        long[] cien = new long[100];
        for (int i = 0; i < 100; i++) {
            cien[i] = 100 - i; // desordenado a propósito: 100..1
        }
        assertThat(MonitorCarga.percentil95(cien)).isEqualTo(95);
        assertThat(MonitorCarga.percentil95(new long[] {10, 20, 30, 40})).isEqualTo(40);
    }

    @Test
    @DisplayName("ARQ-06 ASR1: sin carga la búsqueda es la completa")
    void sinCargaNoDegrada() {
        MonitorCarga m = monitor(false, umbrales());
        peticiones(m, 50, 40);
        reloj.avanzar(Duration.ofSeconds(2));
        assertThat(m.degradado()).isFalse();
        assertThat(m.estado().origen()).isEqualTo(MonitorCarga.Origen.NINGUNO);
        assertThat(m.estado().p95Ms()).isEqualTo(40);
    }

    @Test
    @DisplayName("ARQ-06 ASR1: al llegar al umbral de peticiones en curso se degrada de inmediato")
    void entraPorPeticionesEnCurso() {
        MonitorCarga m = monitor(false, umbrales());
        for (int i = 0; i < 9; i++) {
            m.iniciarPeticion();
        }
        assertThat(m.degradado()).isFalse();
        m.iniciarPeticion(); // la décima
        assertThat(m.degradado()).isTrue();
        assertThat(m.estado().origen()).isEqualTo(MonitorCarga.Origen.AUTOMATICO);
        assertThat(m.estado().enCurso()).isEqualTo(10);
    }

    @Test
    @DisplayName("ARQ-06 ASR1: el p95 de la ventana por encima del umbral degrada, pero solo con muestras suficientes")
    void entraPorP95ConMuestrasSuficientes() {
        MonitorCarga pocas = monitor(false, umbrales());
        peticiones(pocas, 19, 5_000); // menos de las 20 mínimas: ruido, no decide
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(pocas.degradado()).isFalse();

        MonitorCarga m = monitor(false, umbrales());
        peticiones(m, 20, 1_500);
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(m.degradado()).isTrue();
        assertThat(m.estado().p95Ms()).isEqualTo(1_500);
    }

    @Test
    @DisplayName("ARQ-06: el p95 por debajo del umbral de entrada no degrada (95 % rápidas)")
    void unaMinoriaLentaNoDegrada() {
        MonitorCarga m = monitor(false, umbrales());
        peticiones(m, 96, 100);
        peticiones(m, 4, 9_000); // 4 % lentas: el p95 sigue siendo 100 ms
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(m.degradado()).isFalse();
    }

    @Test
    @DisplayName("ARQ-06: las muestras fuera de la ventana deslizante no cuentan")
    void laVentanaDescartaLoViejo() {
        MonitorCarga m = monitor(false, umbrales());
        peticiones(m, 30, 100);
        reloj.avanzar(Duration.ofSeconds(11)); // la ventana es de 10 s
        assertThat(m.degradado()).isFalse();
        assertThat(m.estado().muestras()).isZero();
        assertThat(m.estado().p95Ms()).isZero();
    }

    @Test
    @DisplayName("ARQ-06 histéresis: entre el umbral de salida y el de entrada se queda degradada; sale con carga baja y permanencia cumplida")
    void histeresisYSalida() {
        MonitorCarga m = monitor(false, umbrales());
        peticiones(m, 30, 1_500);
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(m.degradado()).isTrue();

        // pasó la permanencia, pero el p95 (700 ms) está entre la salida (500) y la entrada (1000): sigue degradada
        reloj.avanzar(Duration.ofSeconds(16));
        peticiones(m, 30, 700);
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(m.degradado()).isTrue();

        // p95 bajo (200 ms): vuelve a la completa
        reloj.avanzar(Duration.ofSeconds(11)); // las muestras de 700 ms salen de la ventana
        peticiones(m, 30, 200);
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(m.degradado()).isFalse();
        assertThat(m.estado().cambios()).isEqualTo(2);
    }

    @Test
    @DisplayName("ARQ-06 histéresis: no se sale antes de la permanencia mínima aunque la carga ya bajó")
    void permanenciaMinima() {
        MonitorCarga m = monitor(false, umbrales());
        peticiones(m, 30, 1_500);
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(m.degradado()).isTrue();
        reloj.avanzar(Duration.ofSeconds(11)); // ventana vacía: sin carga desde hace 11 s, pero solo van 12 s degradada
        assertThat(m.degradado()).isTrue();
        reloj.avanzar(Duration.ofSeconds(4)); // 16 s degradada
        assertThat(m.degradado()).isFalse();
    }

    @Test
    @DisplayName("ARQ-06 histéresis: con más peticiones en curso que el umbral de salida no se sale aunque el p95 sea bueno")
    void noSaleSiSigueHabiendoMuchasEnCurso() {
        MonitorCarga m = monitor(false, umbrales());
        for (int i = 0; i < 10; i++) {
            m.iniciarPeticion();
        }
        assertThat(m.degradado()).isTrue();
        for (int i = 0; i < 4; i++) {
            m.terminarPeticion(10); // quedan 6 en curso: por debajo de la entrada (10) pero sobre la salida (4)
        }
        reloj.avanzar(Duration.ofSeconds(30));
        assertThat(m.degradado()).isTrue();
        for (int i = 0; i < 2; i++) {
            m.terminarPeticion(10); // quedan 4
        }
        reloj.avanzar(Duration.ofSeconds(2));
        assertThat(m.degradado()).isFalse();
    }

    @Test
    @DisplayName("ARQ-06 histéresis: una carga que ronda el umbral no hace oscilar el modo (máximo un cambio por permanencia)")
    void noOscilaAlRondarElUmbral() {
        MonitorCarga m = monitor(false, umbrales());
        // 60 s alternando cada segundo entre p95 justo sobre la entrada (1100 ms) y justo bajo ella (900 ms)
        for (int segundo = 0; segundo < 60; segundo++) {
            peticiones(m, 25, segundo % 2 == 0 ? 1_100 : 900);
            reloj.avanzar(Duration.ofSeconds(1));
            m.degradado();
        }
        // sin histéresis serían decenas de cambios; con ella: entra una vez y, como el p95 nunca baja de 500 ms, no sale
        assertThat(m.estado().cambios()).isEqualTo(1);
        assertThat(m.degradado()).isTrue();
    }

    @Test
    @DisplayName("ARQ-06 RNF1: el forzado manual degrada siempre, aunque no haya carga, y el monitor solo informa")
    void forzadoManual() {
        MonitorCarga m = monitor(true, umbrales());
        assertThat(m.degradado()).isTrue();
        reloj.avanzar(Duration.ofHours(1));
        peticiones(m, 5, 10);
        assertThat(m.degradado()).isTrue();
        assertThat(m.estado().origen()).isEqualTo(MonitorCarga.Origen.FORZADO);
        assertThat(m.estado().cambios()).isZero();
    }

    @Test
    @DisplayName("ARQ-06: con la degradación automática desactivada solo el forzado puede degradar")
    void sinAutomatica() {
        UmbralesDegradacion u = umbrales();
        MonitorCarga m = monitor(false, new UmbralesDegradacion(false, u.enCursoEntrada(), u.enCursoSalida(), u.p95EntradaMs(),
                u.p95SalidaMs(), u.ventana(), u.muestrasMinimas(), u.permanenciaMinima(), u.intervaloEvaluacion()));
        for (int i = 0; i < 50; i++) {
            m.iniciarPeticion();
        }
        peticiones(m, 30, 9_000);
        reloj.avanzar(Duration.ofSeconds(2));
        assertThat(m.degradado()).isFalse();
    }

    @Test
    @DisplayName("ARQ-06: el buffer circular acotado soporta más muestras que su capacidad sin romperse")
    void bufferCircularAcotado() {
        MonitorCarga m = monitor(false, umbrales());
        peticiones(m, MonitorCarga.CAPACIDAD + 1_000, 50);
        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(m.degradado()).isFalse();
        assertThat(m.estado().muestras()).isEqualTo(MonitorCarga.CAPACIDAD);
    }

    @Test
    @DisplayName("ARQ-06: los umbrales incoherentes (salida ≥ entrada) se rechazan al arrancar")
    void umbralesIncoherentes() {
        assertThatThrownBy(() -> new UmbralesDegradacion(true, 10, 10, 1000, 500, Duration.ofSeconds(10), 20, Duration.ofSeconds(15), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UmbralesDegradacion(true, 10, 4, 1000, 1000, Duration.ofSeconds(10), 20, Duration.ofSeconds(15), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UmbralesDegradacion(true, 10, 4, 1000, 500, Duration.ZERO, 20, Duration.ofSeconds(15), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UmbralesDegradacion(true, 10, 4, 1000, 500, Duration.ofSeconds(10), 0, Duration.ofSeconds(15), Duration.ofSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(UmbralesDegradacion.porDefecto().enCursoEntrada()).isGreaterThan(UmbralesDegradacion.porDefecto().enCursoSalida());
    }
}
