package co.edu.unbosque.biohub.cientifico.registro.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.cientifico.eventos.domain.HallazgoReclasificado;
import co.edu.unbosque.biohub.cientifico.eventos.domain.HallazgoRegistrado;
import co.edu.unbosque.biohub.cientifico.registro.domain.CambioAuditoria;
import co.edu.unbosque.biohub.cientifico.registro.domain.CambioAuditoriaRepository;
import co.edu.unbosque.biohub.cientifico.registro.domain.CategoriaUicn;
import co.edu.unbosque.biohub.cientifico.registro.domain.Hallazgo;
import co.edu.unbosque.biohub.cientifico.registro.domain.HallazgoRepository;
import co.edu.unbosque.biohub.cientifico.registro.domain.VersionHallazgo;
import co.edu.unbosque.biohub.cientifico.registro.domain.VersionHallazgoRepository;
import co.edu.unbosque.biohub.cientifico.registro.domain.EstadoConservacion;

/**
 * Registro y reclasificación de hallazgos (HU-01, HU-03) con su historial inmutable (HU-05).
 *
 * <p><b>Orden de operaciones</b> (04-contrato-api §6, obligación 1): el hallazgo se inserta primero y
 * solo después se anuncia a ubicación, todo dentro de la misma transacción. Si ubicación rechaza los
 * datos o el cifrado falla, la excepción revierte el registro completo y no queda un hallazgo sin
 * ubicación. Ubicación se entera por un evento de dominio síncrono, no por una llamada directa, para
 * que registro no importe sus clases internas (SAD §2.5, §7.1).
 *
 * <p><b>Coordenadas</b>: entran por el comando, se pasan al evento de dominio para que ubicación las
 * cifre, y ahí termina su recorrido. No se guardan en ninguna tabla de este módulo, no entran al
 * historial y no viajan al evento público (D14, RNF9).
 */
@Service
public class HallazgoService {

    private static final int MAX_NOMBRE = 150;
    private static final int MAX_FAMILIA = 100;
    private static final int MAX_TIPO_ESPECIMEN = 80;
    private static final int MAX_DEPARTAMENTO = 60;
    private static final int MAX_DESCRIPCION = 5000;
    private static final int MAX_JUSTIFICACION = 1000;

    private final HallazgoRepository hallazgos;
    private final VersionHallazgoRepository versiones;
    private final CambioAuditoriaRepository auditoria;
    private final ConstructorSnapshotPublico snapshotPublico;
    private final ValidadorTextoPublico validadorTexto;
    private final ApplicationEventPublisher publicador;
    private final Clock reloj;

    @SuppressWarnings("java:S107")
    public HallazgoService(HallazgoRepository hallazgos, VersionHallazgoRepository versiones,
                           CambioAuditoriaRepository auditoria, ConstructorSnapshotPublico snapshotPublico,
                           ValidadorTextoPublico validadorTexto, ApplicationEventPublisher publicador, Clock reloj) {
        this.hallazgos = hallazgos;
        this.versiones = versiones;
        this.auditoria = auditoria;
        this.snapshotPublico = snapshotPublico;
        this.validadorTexto = validadorTexto;
        this.publicador = publicador;
        this.reloj = reloj;
    }

    /**
     * Registra un hallazgo nuevo (HU-01) y entrega su ubicación a ubicación para que la cifre.
     *
     * @param investigador {@code preferred_username} del JWT de quien registra
     * @throws DatosHallazgoInvalidosException si falta un obligatorio o un texto trae coordenadas
     */
    @Transactional
    public ResultadoRegistro registrar(RegistrarHallazgoComando comando, String investigador) {
        validarComunes(comando.nombreCientifico(), comando.familia(), comando.fechaRecoleccion(),
                comando.departamento(), comando.descripcion(), comando.nombreComun(), comando.tipoEspecimen());
        exigirTexto("investigador", investigador, 100);
        // HU-01: el tipo de espécimen es obligatorio al registrar, aunque el evento público lo admita vacío.
        exigirTexto("tipoEspecimen", comando.tipoEspecimen(), MAX_TIPO_ESPECIMEN);
        validarCoordenadas(comando.latitud(), comando.longitud());

        CategoriaUicn categoria = comando.categoriaUicn() == null ? CategoriaUicn.NE : comando.categoriaUicn();
        EstadoConservacion estado = categoria.estadoConservacion();

        Hallazgo hallazgo = new Hallazgo(UUID.randomUUID(), comando.nombreCientifico().strip(),
                limpiar(comando.nombreComun()), comando.familia().strip(), limpiar(comando.tipoEspecimen()),
                comando.fechaRecoleccion(), investigador.strip(), comando.departamento().strip(),
                estado, categoria, limpiar(comando.descripcion()), comando.atributos(), null);

        // 1. El hallazgo existe antes de que nadie más lo mire.
        hallazgos.saveAndFlush(hallazgo);

        // 2. Historial: primera versión, sin justificación (HU-05).
        Map<String, Object> datos = snapshotPublico.construir(hallazgo);
        versiones.save(VersionHallazgo.primeraVersion(hallazgo.getId())
                .snapshot(datos)
                .autor(investigador.strip())
                .build());

        // 3. Ubicación cifra las coordenadas dentro de esta misma transacción. Si rechaza los datos,
        //    su excepción revierte todo; se traduce a un error de registro para responder 400 con el campo.
        try {
            publicador.publishEvent(new HallazgoRegistrado(hallazgo.getId(), hallazgo.getVersion(),
                    hallazgo.getNombreCientifico(), hallazgo.getFechaRecoleccion(), hallazgo.getInvestigador(),
                    hallazgo.getDepartamento(), estado.name(), comando.latitud(), comando.longitud(),
                    Instant.now(reloj)));
        } catch (RuntimeException e) {
            throw TraductorErroresDeUbicacion.traducir(e);
        }

        // 4. El resto del mundo se entera solo si el commit funciona.
        publicador.publishEvent(new CambioHallazgoConfirmado(
                CambioHallazgoConfirmado.TIPO_REGISTRADO, investigador.strip(), datos));

        return new ResultadoRegistro(hallazgo.getId(), hallazgo.getVersion());
    }

    /**
     * Reclasifica o corrige un hallazgo existente (HU-03). Exige justificación científica y deja
     * constancia campo a campo en la auditoría (HU-05).
     *
     * @param autor {@code preferred_username} del JWT de quien reclasifica
     * @throws HallazgoNoEncontradoException si el hallazgo no existe o está anulado
     * @throws DatosHallazgoInvalidosException si falta la justificación o un dato es inválido
     */
    @Transactional
    public ResultadoRegistro reclasificar(UUID hallazgoId, ReclasificarHallazgoComando comando, String autor) {
        exigirTexto("justificacion", comando.justificacion(), MAX_JUSTIFICACION);
        exigirTexto("autor", autor, 100);

        Hallazgo hallazgo = hallazgos.buscarVigente(hallazgoId)
                .orElseThrow(() -> new HallazgoNoEncontradoException(hallazgoId));

        // Se valida lo que llega; lo que viene nulo se deja como está.
        if (comando.nombreCientifico() != null) {
            exigirTexto("nombreCientifico", comando.nombreCientifico(), MAX_NOMBRE);
        }
        if (comando.familia() != null) {
            exigirTexto("familia", comando.familia(), MAX_FAMILIA);
        }
        if (comando.departamento() != null) {
            exigirTexto("departamento", comando.departamento(), MAX_DEPARTAMENTO);
        }
        if (comando.fechaRecoleccion() != null) {
            validarFecha(comando.fechaRecoleccion());
        }
        validarLongitud("nombreComun", comando.nombreComun(), MAX_NOMBRE);
        validarLongitud("tipoEspecimen", comando.tipoEspecimen(), MAX_TIPO_ESPECIMEN);
        validarLongitud("descripcion", comando.descripcion(), MAX_DESCRIPCION);

        Hallazgo previo = copiaDeReferencia(hallazgo);
        CategoriaUicn categoria = comando.categoriaUicn();
        EstadoConservacion estado = categoria == null ? null : categoria.estadoConservacion();

        int versionNueva = hallazgo.reclasificar(limpiar(comando.nombreCientifico()), limpiar(comando.nombreComun()),
                limpiar(comando.familia()), limpiar(comando.tipoEspecimen()), comando.fechaRecoleccion(),
                limpiar(comando.departamento()), estado, categoria, limpiar(comando.descripcion()),
                comando.atributos());

        hallazgos.saveAndFlush(hallazgo);

        Map<String, Object> datos = snapshotPublico.construir(hallazgo);
        String justificacion = comando.justificacion().strip();

        versiones.save(VersionHallazgo.siguienteVersion(hallazgo.getId(), versionNueva - 1)
                .snapshot(datos)
                .justificacion(justificacion)
                .autor(autor.strip())
                .build());

        auditoria.saveAll(diferencias(previo, hallazgo, versionNueva, justificacion, autor.strip()));

        try {
            publicador.publishEvent(new HallazgoReclasificado(hallazgo.getId(), versionNueva,
                    hallazgo.getNombreCientifico(), hallazgo.getFechaRecoleccion(), hallazgo.getDepartamento(),
                    hallazgo.getEstadoConservacion().name(), Instant.now(reloj)));
        } catch (RuntimeException e) {
            throw TraductorErroresDeUbicacion.traducir(e);
        }

        publicador.publishEvent(new CambioHallazgoConfirmado(
                CambioHallazgoConfirmado.TIPO_RECLASIFICADO, autor.strip(), datos));

        return new ResultadoRegistro(hallazgo.getId(), versionNueva);
    }

    /** Copia de los valores anteriores, para poder comparar después de aplicar los cambios. */
    private static Hallazgo copiaDeReferencia(Hallazgo h) {
        return new Hallazgo(h.getId(), h.getNombreCientifico(), h.getNombreComun(), h.getFamilia(),
                h.getTipoEspecimen(), h.getFechaRecoleccion(), h.getInvestigador(), h.getDepartamento(),
                h.getEstadoConservacion(), h.getCategoriaUicn(), h.getDescripcion(), h.getAtributos(),
                h.getPlantillaVersion());
    }

    /** Una fila de auditoría por cada campo que cambió de verdad (HU-05). */
    private static List<CambioAuditoria> diferencias(Hallazgo previo, Hallazgo actual, int versionNueva,
                                                     String justificacion, String autor) {
        List<CambioAuditoria> cambios = new ArrayList<>();
        agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                "nombreCientifico", previo.getNombreCientifico(), actual.getNombreCientifico());
        agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                "nombreComun", previo.getNombreComun(), actual.getNombreComun());
        agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                "familia", previo.getFamilia(), actual.getFamilia());
        agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                "tipoEspecimen", previo.getTipoEspecimen(), actual.getTipoEspecimen());
        agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                "fechaRecoleccion", texto(previo.getFechaRecoleccion()), texto(actual.getFechaRecoleccion()));
        agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                "departamento", previo.getDepartamento(), actual.getDepartamento());
        agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                "estadoConservacion", texto(previo.getEstadoConservacion()), texto(actual.getEstadoConservacion()));
        agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                "categoriaUicn", texto(previo.getCategoriaUicn()), texto(actual.getCategoriaUicn()));
        agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                "descripcion", previo.getDescripcion(), actual.getDescripcion());
        if (!Objects.equals(previo.getAtributos(), actual.getAtributos())) {
            agregarSiCambio(cambios, previo, actual, versionNueva, justificacion, autor,
                    "atributos", texto(previo.getAtributos()), texto(actual.getAtributos()));
        }
        return cambios;
    }

    @SuppressWarnings("java:S107")
    private static void agregarSiCambio(List<CambioAuditoria> acumulador, Hallazgo previo, Hallazgo actual,
                                        int versionNueva, String justificacion, String autor,
                                        String campo, String valorPrevio, String valorNuevo) {
        if (!Objects.equals(valorPrevio, valorNuevo)) {
            acumulador.add(new CambioAuditoria(actual.getId(), previo.getVersion(), versionNueva,
                    campo, valorPrevio, valorNuevo, justificacion, autor));
        }
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static String limpiar(String valor) {
        return valor == null || valor.isBlank() ? null : valor.strip();
    }

    @SuppressWarnings("java:S107")
    private void validarComunes(String nombreCientifico, String familia, LocalDate fechaRecoleccion,
                                String departamento, String descripcion, String nombreComun, String tipoEspecimen) {
        exigirTexto("nombreCientifico", nombreCientifico, MAX_NOMBRE);
        exigirTexto("familia", familia, MAX_FAMILIA);
        exigirTexto("departamento", departamento, MAX_DEPARTAMENTO);
        if (fechaRecoleccion == null) {
            throw new DatosHallazgoInvalidosException("fechaRecoleccion", "obligatoria y no futura");
        }
        validarFecha(fechaRecoleccion);
        validarLongitud("nombreComun", nombreComun, MAX_NOMBRE);
        validarLongitud("tipoEspecimen", tipoEspecimen, MAX_TIPO_ESPECIMEN);
        validarLongitud("descripcion", descripcion, MAX_DESCRIPCION);
        // Antes de tocar la base de datos: ningún texto público puede traer coordenadas escritas.
        validadorTexto.verificar("nombreCientifico", nombreCientifico);
        validadorTexto.verificar("nombreComun", nombreComun);
        validadorTexto.verificar("familia", familia);
        validadorTexto.verificar("tipoEspecimen", tipoEspecimen);
        validadorTexto.verificar("departamento", departamento);
        validadorTexto.verificar("descripcion", descripcion);
    }

    private void validarFecha(LocalDate fecha) {
        if (fecha.isAfter(LocalDate.now(reloj))) {
            throw new DatosHallazgoInvalidosException("fechaRecoleccion", "obligatoria y no futura");
        }
    }

    private static void validarCoordenadas(Double latitud, Double longitud) {
        if (latitud == null && longitud == null) {
            return;
        }
        if (latitud == null || longitud == null) {
            throw new DatosHallazgoInvalidosException("latitud", "latitud y longitud van juntas");
        }
        if (!Double.isFinite(latitud) || latitud < -90 || latitud > 90) {
            throw new DatosHallazgoInvalidosException("latitud", "latitud fuera de rango");
        }
        if (!Double.isFinite(longitud) || longitud < -180 || longitud > 180) {
            throw new DatosHallazgoInvalidosException("longitud", "longitud fuera de rango");
        }
    }

    private static void exigirTexto(String campo, String valor, int maximo) {
        if (valor == null || valor.isBlank()) {
            throw new DatosHallazgoInvalidosException(campo, "obligatorio, maximo " + maximo + " caracteres");
        }
        validarLongitud(campo, valor, maximo);
    }

    private static void validarLongitud(String campo, String valor, int maximo) {
        if (valor != null && valor.strip().length() > maximo) {
            throw new DatosHallazgoInvalidosException(campo, "obligatorio, maximo " + maximo + " caracteres");
        }
    }
}
