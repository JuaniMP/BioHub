package co.edu.unbosque.biohub.gateway.domain;

/** Estado guardado de una API Key. Una key vencida sigue ACTIVA: su vencimiento lo decide expira_en. */
public enum EstadoApiKey {
    ACTIVA,
    REVOCADA
}
