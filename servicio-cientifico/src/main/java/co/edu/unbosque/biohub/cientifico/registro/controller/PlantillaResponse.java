package co.edu.unbosque.biohub.cientifico.registro.controller;

import java.time.Instant;
import java.util.List;

import co.edu.unbosque.biohub.cientifico.registro.domain.AtributoPlantilla;
import co.edu.unbosque.biohub.cientifico.registro.domain.FichaPlantilla;

/** Plantilla de ficha taxonómica con sus atributos (HU-02). */
public record PlantillaResponse(
        String nombreCientifico,
        int version,
        List<AtributoPlantilla> atributos,
        String creadoPor,
        Instant creadoEn) {

    public static PlantillaResponse de(FichaPlantilla p) {
        return new PlantillaResponse(p.getNombreCientifico(), p.getVersion(), p.getAtributos(),
                p.getCreadoPor(), p.getCreadoEn());
    }
}
