package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

/** Par latitud/longitud en grados. Nunca se imprime: {@code toString()} está enmascarado. */
public record Coordenadas(double latitud, double longitud) {

    public Coordenadas {
        if (!Double.isFinite(latitud) || !Double.isFinite(longitud)) {
            throw new IllegalArgumentException("Coordenadas no finitas");
        }
        if (latitud < -90 || latitud > 90 || longitud < -180 || longitud > 180) {
            throw new IllegalArgumentException("Coordenadas fuera de rango");
        }
    }

    @Override
    public String toString() {
        return "Coordenadas[***]";
    }
}
