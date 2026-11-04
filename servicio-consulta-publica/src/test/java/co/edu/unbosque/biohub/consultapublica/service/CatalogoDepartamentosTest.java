package co.edu.unbosque.biohub.consultapublica.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import co.edu.unbosque.biohub.consultapublica.domain.Departamento;

class CatalogoDepartamentosTest {

    private static final List<Departamento> DATOS = List.of(new Departamento("15", "Boyacá", "Andina"),
            new Departamento("11", "Bogotá D.C.", "Andina"), new Departamento("88", "San Andrés y Providencia", "Insular"));

    @Test
    void resuelveCodigoYNombreSinTildesNiMayusculas() {
        CatalogoDepartamentos c = new CatalogoDepartamentos(() -> DATOS);
        for (String v : new String[] {"15", " 15 ", "Boyacá", "boyaca", "BOYACÁ"}) {
            assertThat(c.resolver(v)).hasValueSatisfying(d -> assertThat(d.nombre()).isEqualTo("Boyacá"));
        }
        assertThat(c.resolver("Bogotá D.C.")).isPresent();
        assertThat(c.resolver("bogota")).hasValueSatisfying(d -> assertThat(d.codigo()).isEqualTo("11"));
        assertThat(c.resolver("san andres y providencia").get().region()).isEqualTo("Insular");
    }

    @Test
    void desconocidoOVacioNoSeResuelve() {
        CatalogoDepartamentos c = new CatalogoDepartamentos(() -> DATOS);
        assertThat(c.resolver("Atlantis")).isEmpty();
        assertThat(c.resolver("5")).isEmpty();
        assertThat(c.resolver("  ")).isEmpty();
        assertThat(c.resolver(null)).isEmpty();
        assertThat(c.resolver("15; DROP TABLE departamento")).isEmpty();
    }

    @Test
    void cargaUnaSolaVezConExitoPeroReintentaSiLaBdFallo() {
        AtomicInteger llamadas = new AtomicInteger();
        CatalogoDepartamentos c = new CatalogoDepartamentos(() -> {
            if (llamadas.incrementAndGet() == 1) {
                throw new org.springframework.dao.DataAccessResourceFailureException("bd caída");
            }
            return DATOS;
        });
        assertThatThrownBy(() -> c.resolver("15")).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(c.resolver("15")).isPresent();
        assertThat(c.resolver("11")).isPresent();
        assertThat(llamadas).hasValue(2);
    }
}
