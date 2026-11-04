package co.edu.unbosque.biohub.cientifico.ubicacion.domain;

import java.util.Arrays;

/** Texto cifrado (cifrado ‖ tag GCM), nonce de 12 bytes y versión de la llave usada. */
public record CoordenadasCifradas(byte[] textoCifrado, byte[] nonce, String claveVersion) {

    @Override
    public boolean equals(Object o) {
        return o instanceof CoordenadasCifradas otra
                && Arrays.equals(textoCifrado, otra.textoCifrado)
                && Arrays.equals(nonce, otra.nonce)
                && java.util.Objects.equals(claveVersion, otra.claveVersion);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * Arrays.hashCode(textoCifrado) + Arrays.hashCode(nonce)) + java.util.Objects.hashCode(claveVersion);
    }

    @Override
    public String toString() {
        return "CoordenadasCifradas[claveVersion=" + claveVersion + ", datos=***]";
    }
}
