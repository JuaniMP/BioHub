package co.edu.unbosque.biohub.cientifico.registro.service;

/** No hay plantilla de ficha registrada para esa especie. */
public class PlantillaNoEncontradaException extends RuntimeException {

    private final transient String nombreCientifico;

    public PlantillaNoEncontradaException(String nombreCientifico) {
        super("Plantilla no encontrada");
        this.nombreCientifico = nombreCientifico;
    }

    public String nombreCientifico() {
        return nombreCientifico;
    }
}
