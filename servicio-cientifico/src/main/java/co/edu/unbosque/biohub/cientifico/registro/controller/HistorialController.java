package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import co.edu.unbosque.biohub.cientifico.registro.service.ComparacionVersiones;
import co.edu.unbosque.biohub.cientifico.registro.service.HistorialService;

/**
 * Historial inmutable de un hallazgo y comparación entre versiones (HU-05).
 *
 * <p>Pensado para el auditor científico, aunque también lo consultan los roles científicos: ver el
 * historial es una lectura y no permite alterar nada.
 */
@RestController
@RequestMapping("/api/cientifico/hallazgos/{id}")
public class HistorialController {

    private final HistorialService historial;

    public HistorialController(HistorialService historial) {
        this.historial = historial;
    }

    /** Versiones del hallazgo, de la más reciente a la más antigua. */
    @GetMapping("/versiones")
    @PreAuthorize("hasAnyRole('auditor_cientifico', 'cientifico_alto_rango', 'administrador')")
    public List<VersionResponse> versiones(@PathVariable UUID id) {
        return historial.versionesDe(id).stream().map(VersionResponse::de).toList();
    }

    /** Cambios campo a campo, con valor previo, valor nuevo, justificación, autor y fecha. */
    @GetMapping("/auditoria")
    @PreAuthorize("hasAnyRole('auditor_cientifico', 'cientifico_alto_rango', 'administrador')")
    public List<CambioAuditoriaResponse> auditoria(@PathVariable UUID id) {
        return historial.cambiosDe(id).stream().map(CambioAuditoriaResponse::de).toList();
    }

    /**
     * Compara dos versiones y devuelve solo lo que cambió, que es lo que el auditor necesita resaltar.
     *
     * @param desde número de una de las versiones
     * @param hasta número de la otra versión; el orden no importa
     */
    @GetMapping("/comparar")
    @PreAuthorize("hasAnyRole('auditor_cientifico', 'cientifico_alto_rango', 'administrador')")
    public ComparacionVersiones comparar(@PathVariable UUID id,
                                         @RequestParam int desde,
                                         @RequestParam int hasta) {
        return historial.comparar(id, desde, hasta);
    }
}
