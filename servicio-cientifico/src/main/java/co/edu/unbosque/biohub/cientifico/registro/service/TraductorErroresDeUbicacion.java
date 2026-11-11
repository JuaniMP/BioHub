package co.edu.unbosque.biohub.cientifico.registro.service;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Convierte en errores propios de registro las excepciones que levanta el módulo de ubicación cuando
 * rechaza los datos de un hallazgo.
 *
 * <p>Hace falta porque el contrato obliga a responder {@code 400} con el campo que falló
 * (04-contrato-api §6, obligación 1), pero D53 prohíbe que registro importe clases de ubicación, y el
 * manejador de excepciones de ese módulo solo cubre sus propios controladores. La traducción se hace
 * entonces por el <b>nombre simple</b> de la clase, que no acopla a registro con su paquete.
 *
 * <p>Es el único punto del módulo que sabe que al otro lado hay un vecino con sus propias excepciones.
 * Lo que no reconoce lo deja pasar tal cual, para no convertir en {@code 400} un fallo que en realidad
 * es un error del servidor.
 */
final class TraductorErroresDeUbicacion {

    private static final String DATOS_INVALIDOS = "UbicacionInvalidaException";
    private static final String NO_REGISTRADA = "UbicacionNoEncontradaException";

    private TraductorErroresDeUbicacion() {
    }

    /**
     * @return la excepción equivalente de registro, o la misma excepción si no viene de ubicación
     */
    static RuntimeException traducir(RuntimeException e) {
        String clase = e.getClass().getSimpleName();
        if (DATOS_INVALIDOS.equals(clase)) {
            return new DatosHallazgoInvalidosException(campoDe(e), e.getMessage());
        }
        if (NO_REGISTRADA.equals(clase)) {
            return new UbicacionDelHallazgoNoRegistradaException(e.getMessage());
        }
        return e;
    }

    /**
     * Lee el método {@code campo()} de la excepción de ubicación. Si cambiara de nombre, se usa
     * {@code ubicacion} como campo genérico en vez de fallar: el usuario debe recibir su error de
     * validación igual.
     */
    private static String campoDe(RuntimeException e) {
        try {
            Method campo = e.getClass().getMethod("campo");
            Object valor = campo.invoke(e);
            return valor == null ? "ubicacion" : valor.toString();
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignorada) {
            return "ubicacion";
        }
    }
}
