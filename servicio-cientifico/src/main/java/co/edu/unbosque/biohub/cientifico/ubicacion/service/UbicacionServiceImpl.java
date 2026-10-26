package co.edu.unbosque.biohub.cientifico.ubicacion.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Coordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CoordenadasCifradas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.Departamento;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgo;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.UbicacionHallazgoRepository;

/** Implementación de {@link UbicacionService}: se ejecuta dentro de la transacción de registro. */
@Service
class UbicacionServiceImpl implements UbicacionService {

    static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");
    private static final int MAX_NOMBRE = 150;
    private static final int MAX_INVESTIGADOR = 100;

    private final UbicacionHallazgoRepository repositorio;
    private final CatalogoDepartamentos catalogo;
    private final CifradorCoordenadas cifrador;
    private final Clock reloj;

    UbicacionServiceImpl(UbicacionHallazgoRepository repositorio, CatalogoDepartamentos catalogo,
                         CifradorCoordenadas cifrador, ObjectProvider<Clock> reloj) {
        this.repositorio = repositorio;
        this.catalogo = catalogo;
        this.cifrador = cifrador;
        this.reloj = reloj.getIfAvailable(Clock::systemUTC);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoUbicacion registrar(RegistroUbicacionComando c) {
        if (c == null) {
            throw new UbicacionInvalidaException("comando", "obligatorio");
        }
        validarComunes(c.hallazgoId(), c.version(), c.nombreCientifico(), c.fechaRecoleccion(), c.estadoConservacion());
        if (c.investigador() == null || c.investigador().isBlank() || c.investigador().trim().length() > MAX_INVESTIGADOR) {
            throw new UbicacionInvalidaException("investigador", "obligatorio, máximo 100 caracteres");
        }
        Departamento departamento = resolverDepartamento(c.departamento());
        Optional<Coordenadas> coordenadas = validarCoordenadas(c.latitud(), c.longitud());

        if (repositorio.existsById(c.hallazgoId())) {
            return ResultadoUbicacion.IGNORADA_DUPLICADA;
        }
        UbicacionHallazgo fila = new UbicacionHallazgo(c.hallazgoId(), c.version(), departamento.getCodigo(),
                c.nombreCientifico().trim(), c.fechaRecoleccion(), c.investigador().trim(), c.estadoConservacion());
        if (coordenadas.isPresent()) {
            CoordenadasCifradas cifradas = cifrador.cifrar(c.hallazgoId(), coordenadas.get()); // IllegalStateException revierte la TX
            fila.asignarCifrado(cifradas, Instant.now(reloj));
        }
        repositorio.saveAndFlush(fila);
        return ResultadoUbicacion.APLICADA;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ResultadoUbicacion actualizar(ActualizacionUbicacionComando c) {
        if (c == null) {
            throw new UbicacionInvalidaException("comando", "obligatorio");
        }
        validarComunes(c.hallazgoId(), c.version(), c.nombreCientifico(), c.fechaRecoleccion(), c.estadoConservacion());
        Departamento departamento = resolverDepartamento(c.departamento());

        UbicacionHallazgo fila = repositorio.findById(c.hallazgoId())
                .orElseThrow(() -> new UbicacionNoEncontradaException(c.hallazgoId()));
        if (c.version() <= fila.getVersion()) {
            return ResultadoUbicacion.IGNORADA_VERSION_ANTIGUA;
        }
        fila.actualizarIndice(c.version(), departamento.getCodigo(), c.nombreCientifico().trim(),
                c.fechaRecoleccion(), c.estadoConservacion());
        repositorio.saveAndFlush(fila);
        return ResultadoUbicacion.APLICADA;
    }

    private void validarComunes(java.util.UUID id, int version, String nombre, LocalDate fecha, EstadoConservacion estado) {
        if (id == null) {
            throw new UbicacionInvalidaException("hallazgoId", "obligatorio");
        }
        if (version < 1) {
            throw new UbicacionInvalidaException("version", "debe ser mayor o igual a 1");
        }
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > MAX_NOMBRE) {
            throw new UbicacionInvalidaException("nombreCientifico", "obligatorio, máximo 150 caracteres");
        }
        if (fecha == null || fecha.isAfter(LocalDate.now(reloj.withZone(ZONA_BOGOTA)))) {
            throw new UbicacionInvalidaException("fechaRecoleccion", "obligatoria y no futura");
        }
        if (estado == null) {
            throw new UbicacionInvalidaException("estadoConservacion", "obligatorio");
        }
    }

    private Departamento resolverDepartamento(String texto) {
        return catalogo.resolver(texto)
                .orElseThrow(() -> new UbicacionInvalidaException("departamento", "departamento desconocido"));
    }

    private static Optional<Coordenadas> validarCoordenadas(Double latitud, Double longitud) {
        if (latitud == null && longitud == null) {
            return Optional.empty();
        }
        if (latitud == null || longitud == null) {
            throw new UbicacionInvalidaException("latitud", "latitud y longitud van juntas");
        }
        if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90) {
            throw new UbicacionInvalidaException("latitud", "latitud fuera de rango");
        }
        if (!Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new UbicacionInvalidaException("longitud", "longitud fuera de rango");
        }
        return Optional.of(new Coordenadas(latitud, longitud));
    }
}
