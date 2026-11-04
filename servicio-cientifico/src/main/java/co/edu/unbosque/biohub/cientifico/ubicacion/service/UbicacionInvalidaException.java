package co.edu.unbosque.biohub.cientifico.ubicacion.service;

/** Datos inválidos. El mensaje es seguro para mostrar al usuario (nunca incluye coordenadas). */
public class UbicacionInvalidaException extends RuntimeException {

    private final String campo;

    public UbicacionInvalidaException(String campo, String mensaje) {
        super(mensaje);
        this.campo = campo;
    }

    public String campo() {
        return campo;
    }
}
