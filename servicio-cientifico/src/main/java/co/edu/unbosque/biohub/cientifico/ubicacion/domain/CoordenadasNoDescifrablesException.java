package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

/** No se pudieron descifrar unas coordenadas. No lleva datos sensibles. */
public class CoordenadasNoDescifrablesException extends RuntimeException {

    public enum Motivo { LLAVE_RETIRADA, INTEGRIDAD }

    private final Motivo motivo;

    public CoordenadasNoDescifrablesException(Motivo motivo) {
        super("Coordenadas no descifrables: " + motivo);
        this.motivo = motivo;
    }

    public Motivo motivo() {
        return motivo;
    }
}
