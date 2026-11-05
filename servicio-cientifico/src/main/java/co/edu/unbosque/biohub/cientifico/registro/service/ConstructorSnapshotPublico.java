package co.edu.unbosque.biohub.cientifico.registro.service;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.cientifico.registro.domain.Hallazgo;

/**
 * Arma el snapshot público de un hallazgo: exactamente los campos de {@code datos} que define el
 * contrato (04-contrato-api §7.3), ni uno más.
 *
 * <p>Es el único punto por el que un hallazgo se convierte en algo publicable, y por eso aquí se
 * concentra la garantía de que nunca salgan datos que no deben: coordenadas, investigador,
 * justificación, historial ni atributos internos. Trabajar con una lista fija de campos, en vez de
 * serializar la entidad, evita que un campo nuevo se publique por descuido.
 */
@Component
public class ConstructorSnapshotPublico {

    private final ValidadorTextoPublico validadorTexto;

    public ConstructorSnapshotPublico(ValidadorTextoPublico validadorTexto) {
        this.validadorTexto = validadorTexto;
    }

    /**
     * @return el snapshot listo para viajar en el evento
     * @throws DatosHallazgoInvalidosException si algún texto parece contener coordenadas
     */
    public Map<String, Object> construir(Hallazgo hallazgo) {
        validadorTexto.verificar("nombreCientifico", hallazgo.getNombreCientifico());
        validadorTexto.verificar("nombreComun", hallazgo.getNombreComun());
        validadorTexto.verificar("familia", hallazgo.getFamilia());
        validadorTexto.verificar("tipoEspecimen", hallazgo.getTipoEspecimen());
        validadorTexto.verificar("departamento", hallazgo.getDepartamento());
        validadorTexto.verificar("descripcion", hallazgo.getDescripcion());

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("hallazgoId", hallazgo.getId().toString());
        datos.put("version", hallazgo.getVersion());
        datos.put("nombreCientifico", hallazgo.getNombreCientifico());
        datos.put("nombreComun", hallazgo.getNombreComun());
        datos.put("familia", hallazgo.getFamilia());
        datos.put("tipoEspecimen", hallazgo.getTipoEspecimen());
        datos.put("fechaRecoleccion", hallazgo.getFechaRecoleccion().format(DateTimeFormatter.ISO_LOCAL_DATE));
        datos.put("estadoConservacion", hallazgo.getEstadoConservacion().name());
        datos.put("departamento", hallazgo.getDepartamento());
        datos.put("descripcion", hallazgo.getDescripcion());
        return datos;
    }
}
