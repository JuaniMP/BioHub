package co.edu.unbosque.biohub.consultapublica.eventos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Validation;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import co.edu.unbosque.biohub.consultapublica.domain.Departamento;
import co.edu.unbosque.biohub.consultapublica.domain.EstadoConservacion;
import co.edu.unbosque.biohub.consultapublica.domain.FichaPublica;
import co.edu.unbosque.biohub.consultapublica.service.CatalogoDepartamentos;
import co.edu.unbosque.biohub.consultapublica.soporte.Eventos;

class ValidadorDatosHallazgoTest {

    private final ValidadorDatosHallazgo validador = new ValidadorDatosHallazgo(
            new ObjectMapper().registerModule(new JavaTimeModule()),
            Validation.buildDefaultValidatorFactory().getValidator(),
            new CatalogoDepartamentos(() -> List.of(new Departamento("15", "Boyacá", "Andina"),
                    new Departamento("08", "Atlántico", "Caribe"))));

    private final UUID id = UUID.randomUUID();

    private Map<String, Object> datos() {
        return new HashMap<>(Eventos.datos(id, 1, "Atelopus ebenoides", "NO_AMENAZADA"));
    }

    private void rechaza(Map<String, Object> d) {
        assertThatThrownBy(() -> validador.validar(UUID.randomUUID(), d)).isInstanceOf(EventoInvalidoException.class);
    }

    @Test
    void datosValidosSeConvierteEnFichaConRegionDerivadaDelCatalogo() {
        FichaPublica f = validador.validar(UUID.randomUUID(), datos());
        assertThat(f.hallazgoId()).isEqualTo(id);
        assertThat(f.version()).isEqualTo(1);
        assertThat(f.fechaRecoleccion()).isEqualTo(LocalDate.of(2026, 3, 14));
        assertThat(f.estadoConservacion()).isEqualTo(EstadoConservacion.NO_AMENAZADA);
        assertThat(f.departamentoCodigo()).isEqualTo("15");
        assertThat(f.departamentoNombre()).isEqualTo("Boyacá");
        assertThat(f.regionNombre()).isEqualTo("Andina");
        assertThat(f.descripcion()).isEqualTo("Anfibio endémico de páramo");
    }

    @Test
    void opcionalesPuedenSerNulosOAusentes() {
        Map<String, Object> d = datos();
        d.put("nombreComun", null);
        d.remove("tipoEspecimen");
        d.remove("descripcion");
        FichaPublica f = validador.validar(UUID.randomUUID(), d);
        assertThat(f.nombreComun()).isNull();
        assertThat(f.tipoEspecimen()).isNull();
        assertThat(f.descripcion()).isNull();
    }

    @Test
    void departamentoPorNombreOCodigo() {
        Map<String, Object> d = datos();
        d.put("departamento", "atlantico");
        assertThat(validador.validar(UUID.randomUUID(), d).departamentoCodigo()).isEqualTo("08");
    }

    @Test
    void camposExtraNoProhibidosSeIgnoran() {
        Map<String, Object> d = datos();
        d.put("campoNuevo", "x");
        d.put("anidado", Map.of("a", 1));
        assertThat(validador.validar(UUID.randomUUID(), d)).isNotNull();
    }

    @Test
    void cualquierClaveDeCoordenadasSeRechazaEnCualquierNivelYCapitalizacion() {
        for (String clave : new String[] {"latitud", "longitud", "lat", "lon", "lng", "coordenadas", "coordinates",
                "geometry", "geom", "ubicacionExacta", "Latitud", "COORDENADAS", "UbicacionExacta", "latitude"}) {
            Map<String, Object> plano = datos();
            plano.put(clave, 5.1);
            rechaza(plano);

            Map<String, Object> anidado = datos();
            anidado.put("detalle", Map.of("x", List.of(Map.of(clave, "5.1"))));
            rechaza(anidado);
        }
    }

    @Test
    void coordenadasSeRechazanAunqueElResto_sea_valido_y_sinValorEnElMensaje() {
        Map<String, Object> d = datos();
        d.put("latitud", 5.123456);
        assertThatThrownBy(() -> validador.validar(UUID.randomUUID(), d))
                .isInstanceOf(EventoInvalidoException.class)
                .hasMessageNotContaining("5.123456");
    }

    @Test
    void faltaUnObligatorio() {
        for (String campo : new String[] {"hallazgoId", "version", "nombreCientifico", "familia",
                "fechaRecoleccion", "estadoConservacion", "departamento"}) {
            Map<String, Object> d = datos();
            d.remove(campo);
            rechaza(d);
        }
    }

    @Test
    void obligatoriosEnBlancoONulos() {
        for (String campo : new String[] {"nombreCientifico", "familia", "estadoConservacion", "departamento"}) {
            Map<String, Object> d = datos();
            d.put(campo, "   ");
            rechaza(d);
            d.put(campo, null);
            rechaza(d);
        }
    }

    @Test
    void tiposIncorrectosNoSeCoaccionan() {
        Map<String, Object> d = datos();
        d.put("version", "1");
        rechaza(d);
        d = datos();
        d.put("version", 1.5);
        rechaza(d);
        d = datos();
        d.put("version", 0);
        rechaza(d);
        d = datos();
        d.put("nombreCientifico", 123);
        rechaza(d);
        d = datos();
        d.put("hallazgoId", "no-es-uuid");
        rechaza(d);
        d = datos();
        d.put("fechaRecoleccion", "2026-02-30");
        rechaza(d);
        d = datos();
        d.put("fechaRecoleccion", "14/03/2026");
        rechaza(d);
        d = datos();
        d.put("nombreComun", Map.of("a", "b"));
        rechaza(d);
    }

    @Test
    void longitudesExcedidas() {
        Map<String, Object> d = datos();
        d.put("nombreCientifico", "x".repeat(151));
        rechaza(d);
        d = datos();
        d.put("nombreComun", "x".repeat(151));
        rechaza(d);
        d = datos();
        d.put("familia", "x".repeat(101));
        rechaza(d);
        d = datos();
        d.put("tipoEspecimen", "x".repeat(81));
        rechaza(d);
        d = datos();
        d.put("descripcion", "x".repeat(5001));
        rechaza(d);
        d = datos();
        d.put("descripcion", "x".repeat(5000));
        assertThat(validador.validar(UUID.randomUUID(), d).descripcion()).hasSize(5000);
    }

    @Test
    void departamentoDesconocidoSeRechaza() {
        Map<String, Object> d = datos();
        d.put("departamento", "Atlantis");
        rechaza(d);
    }

    @Test
    void estadoDesconocidoSeGuardaComoEnPeligroYAusenteSeRechaza() {
        Map<String, Object> d = datos();
        d.put("estadoConservacion", "EXTINTA");
        assertThat(validador.validar(UUID.randomUUID(), d).estadoConservacion()).isEqualTo(EstadoConservacion.EN_PELIGRO);
        d.put("estadoConservacion", "en_peligro"); // solo se acepta el valor exacto del contrato
        assertThat(validador.validar(UUID.randomUUID(), d).estadoConservacion()).isEqualTo(EstadoConservacion.EN_PELIGRO);
        d.put("estadoConservacion", "SIN_EVALUAR");
        assertThat(validador.validar(UUID.randomUUID(), d).estadoConservacion()).isEqualTo(EstadoConservacion.SIN_EVALUAR);

        d.remove("estadoConservacion");
        rechaza(d);
    }

    @Test
    void datosNulosOVaciosSeRechazan() {
        assertThatThrownBy(() -> validador.validar(UUID.randomUUID(), null)).isInstanceOf(EventoInvalidoException.class);
        rechaza(Map.of());
    }
}
