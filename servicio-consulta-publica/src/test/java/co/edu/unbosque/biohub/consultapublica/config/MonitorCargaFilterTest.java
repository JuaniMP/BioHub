package co.edu.unbosque.biohub.consultapublica.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import co.edu.unbosque.biohub.consultapublica.service.MonitorCarga;

/** ARQ-06: el filtro alimenta al monitor solo con lecturas públicas y siempre cierra la medición. */
class MonitorCargaFilterTest {

    private final MonitorCarga monitor = mock(MonitorCarga.class);
    private final MonitorCargaFilter filtro = new MonitorCargaFilter(monitor);

    @Test
    @DisplayName("ARQ-06: una lectura pública se cuenta al empezar y se mide al terminar")
    void midePeticionesDeFichas() throws Exception {
        AtomicBoolean dentro = new AtomicBoolean();
        MockFilterChain cadena = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                verify(monitor).iniciarPeticion();
                verify(monitor, never()).terminarPeticion(anyLong());
                dentro.set(true);
            }
        };
        filtro.doFilter(new MockHttpServletRequest("GET", "/api/publica/fichas/3f2a9c1e"), new MockHttpServletResponse(), cadena);
        assertThat(dentro).isTrue();
        InOrder orden = inOrder(monitor);
        orden.verify(monitor).iniciarPeticion();
        orden.verify(monitor).terminarPeticion(anyLong());
    }

    @Test
    @DisplayName("ARQ-06: aunque la petición falle, la medición se cierra (no queda una petición 'en curso' fantasma)")
    void cierraAunqueFalle() {
        MockFilterChain cadena = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                throw new IllegalStateException("boom");
            }
        };
        assertThatThrownBy(() -> filtro.doFilter(new MockHttpServletRequest("GET", "/api/publica/fichas"),
                new MockHttpServletResponse(), cadena)).isInstanceOf(IllegalStateException.class);
        verify(monitor).terminarPeticion(anyLong());
    }

    @Test
    @DisplayName("ARQ-06: las rutas que no son lecturas públicas (eventos, actuator) no cuentan para la carga")
    void ignoraOtrasRutas() throws Exception {
        for (String ruta : new String[] {"/actuator/health", "/api/publica/eventos", "/api/otra"}) {
            filtro.doFilter(new MockHttpServletRequest("GET", ruta), new MockHttpServletResponse(), new MockFilterChain());
        }
        verify(monitor, never()).iniciarPeticion();
        verify(monitor, never()).terminarPeticion(anyLong());
    }
}
