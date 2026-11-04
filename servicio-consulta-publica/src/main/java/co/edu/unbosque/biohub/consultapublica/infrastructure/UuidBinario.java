package co.edu.unbosque.biohub.consultapublica.infrastructure;

import java.nio.ByteBuffer;
import java.util.UUID;

/** Conversión UUID ⇄ BINARY(16) (big-endian). */
final class UuidBinario {

    private UuidBinario() {
    }

    static byte[] aBytes(UUID id) {
        return ByteBuffer.allocate(16).putLong(id.getMostSignificantBits()).putLong(id.getLeastSignificantBits()).array();
    }

    static UUID aUuid(byte[] bytes) {
        ByteBuffer b = ByteBuffer.wrap(bytes);
        return new UUID(b.getLong(), b.getLong());
    }
}
