package co.edu.unbosque.biohub.gateway.service;

/** El circuito del servicio destino está abierto: se responde 503 sin intentar la conexión. */
public class CircuitoAbiertoException extends RuntimeException {

    private final long segundosParaReintentar;

    public CircuitoAbiertoException(long segundosParaReintentar) {
        super("Circuito abierto");
        this.segundosParaReintentar = segundosParaReintentar;
    }

    public long segundosParaReintentar() {
        return segundosParaReintentar;
    }
}
