package co.edu.unbosque.biohub.cientifico.registro.service;

/**
 * El hallazgo no tenía ubicación registrada cuando se intentó actualizarla. Es una inconsistencia entre
 * módulos, no un error de quien llama, así que se responde {@code 409}.
 */
public class UbicacionDelHallazgoNoRegistradaException extends RuntimeException {

    public UbicacionDelHallazgoNoRegistradaException(String mensaje) {
        super(mensaje);
    }
}
