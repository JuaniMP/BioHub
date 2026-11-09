package co.edu.unbosque.biohub.gateway.service;

/** Keycloak rechazó los datos enviados, por ejemplo un nombre con caracteres prohibidos (400). */
public class DatosRechazadosException extends RuntimeException {

    public DatosRechazadosException(String mensaje) {
        super(mensaje);
    }
}
