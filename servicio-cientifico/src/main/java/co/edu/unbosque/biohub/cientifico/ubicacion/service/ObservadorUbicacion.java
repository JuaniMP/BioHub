package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import co.edu.unbosque.biohub.cientifico.eventos.domain.HallazgoReclasificado;
import co.edu.unbosque.biohub.cientifico.eventos.domain.HallazgoRegistrado;

/**
 * Observador de ubicacion (Observer del SAD §6.4.2, ARQ-08, P-A3): escucha los eventos de dominio de
 * {@code cientifico.eventos.domain} y llama a {@link UbicacionService}. Así registro y ubicacion no se importan
 * entre sí: solo comparten el módulo eventos.
 *
 * <p>{@code @EventListener} es SÍNCRONO: se ejecuta en el hilo y en la transacción de quien publica el evento.
 * Este componente no abre transacción: {@link UbicacionService} conserva {@code MANDATORY}, de modo que sin
 * transacción de registro falla con IllegalTransactionStateException, y cualquier excepción (datos inválidos,
 * fallo de cifrado) sube hasta {@code publishEvent} y revierte todo el registro, hallazgo incluido.
 */
@Component
public class ObservadorUbicacion {

    private final UbicacionService ubicacion;

    public ObservadorUbicacion(UbicacionService ubicacion) {
        this.ubicacion = ubicacion;
    }

    @EventListener
    public void alRegistrarse(HallazgoRegistrado e) {
        ubicacion.registrar(new RegistroUbicacionComando(e.hallazgoId(), e.version(), e.nombreCientifico(),
                e.fechaRecoleccion(), e.investigador(), e.departamento(), estado(e.estadoConservacion()),
                e.latitud(), e.longitud()));
    }

    @EventListener
    public void alReclasificarse(HallazgoReclasificado e) {
        ubicacion.actualizar(new ActualizacionUbicacionComando(e.hallazgoId(), e.version(), e.nombreCientifico(),
                e.fechaRecoleccion(), e.departamento(), estado(e.estadoConservacion())));
    }

    /** {@code null} se deja pasar: {@link UbicacionService} responde "obligatorio". Un valor desconocido es inválido. */
    private static EstadoConservacion estado(String texto) {
        if (texto == null) {
            return null;
        }
        try {
            return EstadoConservacion.valueOf(texto.trim());
        } catch (IllegalArgumentException ex) {
            throw new UbicacionInvalidaException("estadoConservacion", "valor no válido");
        }
    }
}
