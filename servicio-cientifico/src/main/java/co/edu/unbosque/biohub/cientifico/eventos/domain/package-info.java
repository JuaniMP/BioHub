/**
 * Eventos del servicio científico. Contiene los eventos de DOMINIO en proceso ({@code EventoDominio},
 * {@code HallazgoRegistrado}, {@code HallazgoReclasificado}: patrón Observer, publicados con
 * {@code ApplicationEventPublisher}, D40/D53) y el modelo de los eventos de INTEGRACIÓN que viajan por RabbitMQ
 * ({@code EventoSobre}).
 */
package co.edu.unbosque.biohub.cientifico.eventos.domain;
