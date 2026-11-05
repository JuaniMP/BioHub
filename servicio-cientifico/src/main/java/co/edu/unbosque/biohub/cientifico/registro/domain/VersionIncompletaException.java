package co.edu.unbosque.biohub.cientifico.registro.domain;

/**
 * El {@code Builder} de {@link VersionHallazgo} recibió datos incompletos. El mensaje es seguro para
 * mostrar al usuario: nunca incluye coordenadas ni valores sensibles.
 */
public class VersionIncompletaException extends RuntimeException {

    private final transient String campo;

    public VersionIncompletaException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
