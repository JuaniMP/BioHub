package co.edu.unbosque.biohub.cientifico.registro.service;

/**
 * Datos de entrada que no cumplen las validaciones de registro. El mensaje es seguro para mostrar al
 * usuario: nunca incluye coordenadas ni valores sensibles.
 */
public class DatosHallazgoInvalidosException extends RuntimeException {

    private final transient String campo;

    public DatosHallazgoInvalidosException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
