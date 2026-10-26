package co.edu.unbosque.biohub.consultapublica.domain;

/** Qué ocurrió al aplicar un evento al modelo de lectura. */
public enum ResultadoProyeccion {
    CREADA, ACTUALIZADA,
    /** Duplicado o versión menor o igual a la guardada: no se tocó nada. */
    IGNORADA;

    public boolean huboCambio() {
        return this != IGNORADA;
    }
}
