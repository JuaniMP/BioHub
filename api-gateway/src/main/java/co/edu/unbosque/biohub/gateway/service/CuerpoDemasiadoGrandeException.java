package co.edu.unbosque.biohub.gateway.service;

/** El cuerpo de la petición supera biohub.gateway.tamano-maximo-cuerpo. */
public class CuerpoDemasiadoGrandeException extends RuntimeException {

    public CuerpoDemasiadoGrandeException(long maximo) {
        super("El cuerpo supera el máximo permitido de " + maximo + " bytes");
    }
}
