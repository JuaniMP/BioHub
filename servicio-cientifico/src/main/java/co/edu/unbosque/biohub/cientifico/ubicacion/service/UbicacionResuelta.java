package co.edu.unbosque.biohub.cientifico.ubicacion.service;

/**
 * Lo que una {@link EstrategiaVisibilidadUbicacion} decidió mostrar de un hallazgo. La ubicación generalizada
 * (departamento y región, D41) acompaña siempre; las coordenadas exactas solo si el estado es {@code VISIBLES}.
 */
public record UbicacionResuelta(
        Estado estado,
        String departamento,
        String departamentoCodigo,
        String region,
        Double latitud,
        Double longitud) {

    /** Estado de las coordenadas de un hallazgo para quien consulta (contrato 04, {@code estadoCoordenadas}). */
    public enum Estado {
        /** El hallazgo se registró sin coordenadas. */
        SIN_COORDENADAS,
        /** Tiene coordenadas, pero quien consulta solo ve la ubicación generalizada. */
        PROTEGIDAS,
        /** Coordenadas exactas entregadas. */
        VISIBLES,
        /** Tiene coordenadas, pero no se pudieron descifrar (llave retirada o dato alterado). */
        NO_DISPONIBLES
    }

    public boolean protegida() {
        return estado == Estado.PROTEGIDAS;
    }

    @Override
    public String toString() {
        return "UbicacionResuelta[estado=" + estado + ", coordenadas=***]";
    }
}
