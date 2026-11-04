package co.edu.unbosque.biohub.cientifico.ubicacion.infrastructure;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasNoDescifrablesException.Motivo;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.ProveedorDeLlaves;

/**
 * AES-256-GCM: texto plano = double lat ‖ double lon (big-endian), nonce de 12 bytes aleatorio en cada
 * cifrado, tag de 128 bits, AAD = UUID canónico del hallazgo (minúsculas, UTF-8).
 */
public class CifradorAesGcm implements CifradorCoordenadas {

    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int BITS_TAG = 128;
    private static final int BYTES_NONCE = 12;
    private static final int BYTES_PLANO = 16;

    private final ProveedorDeLlaves llaves;
    private final SecureRandom aleatorio = new SecureRandom();

    public CifradorAesGcm(ProveedorDeLlaves llaves) {
        this.llaves = llaves;
    }

    @Override
    public CoordenadasCifradas cifrar(UUID hallazgoId, Coordenadas coordenadas) {
        String version = llaves.versionActiva();
        SecretKey llave = llaves.llave(version)
                .orElseThrow(() -> new IllegalStateException("La llave activa no está disponible"));
        byte[] plano = ByteBuffer.allocate(BYTES_PLANO)
                .putDouble(coordenadas.latitud()).putDouble(coordenadas.longitud()).array();
        try {
            return cifrarBytes(hallazgoId, plano, llave, version);
        } finally {
            java.util.Arrays.fill(plano, (byte) 0);
        }
    }

    @Override
    public Coordenadas descifrar(UUID hallazgoId, CoordenadasCifradas cifradas) {
        byte[] plano = descifrarBytes(hallazgoId, cifradas);
        try {
            ByteBuffer buffer = ByteBuffer.wrap(plano);
            double latitud = buffer.getDouble();
            double longitud = buffer.getDouble();
            try {
                return new Coordenadas(latitud, longitud);
            } catch (IllegalArgumentException e) {
                throw new CoordenadasNoDescifrablesException(Motivo.INTEGRIDAD);
            }
        } finally {
            java.util.Arrays.fill(plano, (byte) 0);
        }
    }

    @Override
    public CoordenadasCifradas recifrar(UUID hallazgoId, CoordenadasCifradas cifradas) {
        byte[] plano = descifrarBytes(hallazgoId, cifradas);
        try {
            String version = llaves.versionActiva();
            SecretKey llave = llaves.llave(version)
                    .orElseThrow(() -> new IllegalStateException("La llave activa no está disponible"));
            return cifrarBytes(hallazgoId, plano, llave, version);
        } finally {
            java.util.Arrays.fill(plano, (byte) 0);
        }
    }

    private CoordenadasCifradas cifrarBytes(UUID hallazgoId, byte[] plano, SecretKey llave, String version) {
        byte[] nonce = new byte[BYTES_NONCE];
        aleatorio.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, llave, new GCMParameterSpec(BITS_TAG, nonce));
            cipher.updateAAD(aad(hallazgoId));
            return new CoordenadasCifradas(cipher.doFinal(plano), nonce, version);
        } catch (GeneralSecurityException e) {
            // Mensaje genérico: no se propaga la causa por si arrastrara datos del contexto
            throw new IllegalStateException("No se pudo cifrar la ubicación");
        }
    }

    private byte[] descifrarBytes(UUID hallazgoId, CoordenadasCifradas cifradas) {
        SecretKey llave = llaves.llave(cifradas.claveVersion())
                .orElseThrow(() -> new CoordenadasNoDescifrablesException(Motivo.LLAVE_RETIRADA));
        if (cifradas.nonce() == null || cifradas.nonce().length != BYTES_NONCE
                || cifradas.textoCifrado() == null) {
            throw new CoordenadasNoDescifrablesException(Motivo.INTEGRIDAD);
        }
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.DECRYPT_MODE, llave, new GCMParameterSpec(BITS_TAG, cifradas.nonce()));
            cipher.updateAAD(aad(hallazgoId));
            byte[] plano = cipher.doFinal(cifradas.textoCifrado());
            if (plano.length != BYTES_PLANO) {
                throw new CoordenadasNoDescifrablesException(Motivo.INTEGRIDAD);
            }
            return plano;
        } catch (GeneralSecurityException e) {
            throw new CoordenadasNoDescifrablesException(Motivo.INTEGRIDAD);
        }
    }

    private static byte[] aad(UUID hallazgoId) {
        return hallazgoId.toString().toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    }
}
