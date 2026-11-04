package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgo;

/**
 * Decorator de cifrado (SAD §6.3.2, ASR6/RNF9): al guardar cifra las coordenadas (AES-256-GCM, AAD = UUID del
 * hallazgo) ANTES de que lleguen al componente que accede a MySQL, y solo las descifra en
 * {@link #obtenerUbicacionExacta}. Se cifran todas (D15, D41), sin distinguir el estado de conservación. Es privada
 * del paquete: solo {@link ConfiguracionUbicacion} la instancia (D43, SEC-J-10).
 */
final class DecoradorCifradoCoordenadas extends DecoradorRepositorioUbicaciones {

    private final CifradorCoordenadas cifrador;
    private final Clock reloj;

    DecoradorCifradoCoordenadas(RepositorioUbicaciones envuelto, CifradorCoordenadas cifrador, Clock reloj) {
        super(envuelto);
        this.cifrador = cifrador;
        this.reloj = reloj;
    }

    @Override
    public void guardar(UbicacionHallazgo fila, Coordenadas coordenadas) {
        if (coordenadas != null) {
            // Un fallo de cifrado (IllegalStateException) sube tal cual y revierte la transacción de registro
            CoordenadasCifradas cifradas = cifrador.cifrar(fila.getId(), coordenadas);
            fila.asignarCifrado(cifradas, Instant.now(reloj));
        }
        envuelto.guardar(fila, null); // el componente de abajo nunca ve texto plano
    }

    @Override
    public Coordenadas obtenerUbicacionExacta(String usuario, UUID hallazgoId, CoordenadasCifradas cifradas) {
        return cifrador.descifrar(hallazgoId, cifradas);
    }
}
