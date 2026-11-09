package co.edu.unbosque.biohub.gateway.service;

/** Keycloak no responde, rechazó la cuenta de servicio o falta su configuración (503). */
public class IdentidadNoDisponibleException extends RuntimeException {

    public IdentidadNoDisponibleException(String mensaje) {
        super(mensaje);
    }

    public IdentidadNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
