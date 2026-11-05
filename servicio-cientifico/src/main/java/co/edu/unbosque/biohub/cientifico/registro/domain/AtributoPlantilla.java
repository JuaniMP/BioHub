package co.edu.unbosque.biohub.cientifico.registro.domain;

/**
 * Un campo medible configurable de la ficha de una especie (HU-02): se agrega, se renombra o se
 * deshabilita sin tocar código ni el esquema de la base de datos (ASR5).
 *
 * <p>La {@code clave} es el identificador estable con el que se guarda el valor en el JSON del
 * hallazgo: no cambia nunca, para no corromper los registros históricos. Renombrar el campo cambia
 * solo la {@code etiqueta}. Retirar un campo lo marca {@code habilitado = false}: nunca se borra,
 * porque los hallazgos antiguos siguen teniendo su valor.
 *
 * @param clave       identificador estable, en minúsculas con guion bajo (p. ej. {@code temperatura_agua})
 * @param etiqueta    texto que ve el investigador en el formulario
 * @param tipo        tipo de dato para validar el valor capturado
 * @param obligatorio si el formulario debe exigirlo
 * @param habilitado  si sigue vigente; deshabilitado no se pide, pero se conserva en los hallazgos ya guardados
 * @param unidad      unidad de medida opcional (p. ej. {@code °C})
 */
public record AtributoPlantilla(
        String clave,
        String etiqueta,
        TipoAtributo tipo,
        boolean obligatorio,
        boolean habilitado,
        String unidad) {
}
