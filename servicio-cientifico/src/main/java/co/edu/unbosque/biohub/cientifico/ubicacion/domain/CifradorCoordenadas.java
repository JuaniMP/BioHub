package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import java.util.UUID;

/** Puerto de cifrado de coordenadas (AES-256-GCM, AAD = UUID del hallazgo). */
public interface CifradorCoordenadas {

    /** Cifra con la llave activa y un nonce nuevo. */
    CoordenadasCifradas cifrar(UUID hallazgoId, Coordenadas coordenadas);

    /** @throws CoordenadasNoDescifrablesException llave retirada o integridad fallida */
    Coordenadas descifrar(UUID hallazgoId, CoordenadasCifradas cifradas);

    /** Descifra con la llave de la fila y vuelve a cifrar con la activa; el texto plano no sale del método. */
    CoordenadasCifradas recifrar(UUID hallazgoId, CoordenadasCifradas cifradas);
}
