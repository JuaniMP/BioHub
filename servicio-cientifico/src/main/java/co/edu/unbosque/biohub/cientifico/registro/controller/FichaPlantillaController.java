package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.cientifico.registro.domain.AtributoPlantilla;
import co.edu.unbosque.biohub.cientifico.registro.service.FichaPlantillaService;

/**
 * Configuración de los atributos de la ficha de una especie (HU-02).
 *
 * <p>Es un {@code PUT} y no un {@code POST} porque se envía la lista completa de atributos tal como
 * debe quedar: agregar, renombrar y deshabilitar son el mismo movimiento. Cada llamada guarda una
 * versión nueva de la plantilla, de modo que los hallazgos ya registrados no se ven afectados.
 */
@RestController
@RequestMapping("/api/cientifico/fichas")
public class FichaPlantillaController {

    private final FichaPlantillaService plantillas;

    public FichaPlantillaController(FichaPlantillaService plantillas) {
        this.plantillas = plantillas;
    }

    /** Guarda los atributos de una especie como versión nueva de su plantilla. */
    @PutMapping("/{nombreCientifico}/atributos")
    @PreAuthorize("hasAnyRole('cientifico_alto_rango', 'administrador')")
    public PlantillaResponse configurar(@PathVariable String nombreCientifico,
                                        @RequestBody List<AtributoPlantilla> atributos,
                                        @AuthenticationPrincipal Jwt jwt) {
        String autor = jwt.getClaimAsString("preferred_username");
        return PlantillaResponse.de(plantillas.guardarVersion(nombreCientifico, atributos,
                autor != null ? autor : jwt.getSubject()));
    }

    /**
     * Plantilla vigente de una especie: es lo que el formulario de registro usa para dibujar y validar
     * los campos personalizados (HU-02, criterio 2).
     */
    @GetMapping("/{nombreCientifico}/atributos")
    @PreAuthorize("hasAnyRole('investigador_campo', 'cientifico_alto_rango', 'auditor_cientifico', 'administrador')")
    public PlantillaResponse vigente(@PathVariable String nombreCientifico) {
        return PlantillaResponse.de(plantillas.exigirVigente(nombreCientifico));
    }
}
