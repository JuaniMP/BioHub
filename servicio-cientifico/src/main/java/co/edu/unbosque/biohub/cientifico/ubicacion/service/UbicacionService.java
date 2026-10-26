package co.edu.unbosque.biohub.cientifico.ubicacion.service;

/**
 * API pública del módulo ubicación. Debe invocarse DENTRO de la transacción de registro
 * (propagation = MANDATORY): si no hay transacción activa lanza IllegalTransactionStateException.
 * Cualquier excepción revierte la transacción completa (hallazgo incluido).
 */
public interface UbicacionService {

    /**
     * Alta de la ubicación de un hallazgo recién registrado. Cifra las coordenadas (AES-256-GCM) y
     * guarda el índice de búsqueda interno.
     *
     * @return APLICADA, o IGNORADA_DUPLICADA si ese hallazgoId ya estaba registrado (no cambia nada)
     * @throws UbicacionInvalidaException si algún dato no cumple las validaciones
     */
    ResultadoUbicacion registrar(RegistroUbicacionComando comando);

    /**
     * Reclasificación o edición de los datos indexados. NO cambia coordenadas ni investigador.
     *
     * @return APLICADA, o IGNORADA_VERSION_ANTIGUA si version &lt;= la guardada (no cambia nada)
     * @throws UbicacionInvalidaException si algún dato no cumple las validaciones
     * @throws UbicacionNoEncontradaException si el hallazgo no se registró antes con registrar(...)
     */
    ResultadoUbicacion actualizar(ActualizacionUbicacionComando comando);
}
