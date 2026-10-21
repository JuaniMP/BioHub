/**
 * Servicio Científico. Dividido en dos módulos de paquetes trabajados por personas distintas:
 * <ul>
 *   <li>{@code registro}: hallazgos, fichas e historial.</li>
 *   <li>{@code ubicacion}: cifrado de ubicaciones, gestión de llaves, mapa y búsqueda.</li>
 * </ul>
 * Regla: un módulo NO accede a los paquetes internos del otro; si lo necesita, lo hace a través de
 * un servicio público (clase en {@code service}) del otro módulo o mediante eventos.
 */
package co.edu.unbosque.biohub.cientifico;
