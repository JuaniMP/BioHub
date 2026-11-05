package co.edu.unbosque.biohub.cientifico.registro.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.edu.unbosque.biohub.cientifico.registro.domain.AtributoPlantilla;
import co.edu.unbosque.biohub.cientifico.registro.domain.FichaPlantilla;
import co.edu.unbosque.biohub.cientifico.registro.domain.FichaPlantillaRepository;
import co.edu.unbosque.biohub.cientifico.registro.domain.TipoAtributo;

/**
 * Configuración de fichas taxonómicas con atributos flexibles (HU-02, ASR5).
 *
 * <p>Un atributo nuevo es solo una clave más en un documento JSON: no hace falta migrar el esquema ni
 * desplegar código. Y como cada cambio crea una <b>versión nueva</b> de la plantilla en lugar de
 * modificar la anterior, los hallazgos ya guardados no se alteran ni se corrompen, que es el tercer
 * criterio de aceptación de la historia.
 */
@Service
public class FichaPlantillaService {

    private static final Pattern CLAVE_VALIDA = Pattern.compile("^[a-z][a-z0-9_]{0,59}$");
    private static final int MAX_ATRIBUTOS = 50;

    private final FichaPlantillaRepository plantillas;
    private final ValidadorTextoPublico validadorTexto;

    public FichaPlantillaService(FichaPlantillaRepository plantillas, ValidadorTextoPublico validadorTexto) {
        this.plantillas = plantillas;
        this.validadorTexto = validadorTexto;
    }

    /**
     * Guarda el conjunto de atributos de una especie como una versión nueva de su plantilla.
     *
     * <p>Agregar, renombrar o deshabilitar un atributo son todos el mismo movimiento: se envía la lista
     * completa como debe quedar y se guarda una versión nueva. Un atributo que se retira se envía con
     * {@code habilitado = false}; nunca se elimina, porque los hallazgos antiguos conservan su valor.
     *
     * @param autor {@code preferred_username} de quien configura la plantilla
     * @throws DatosHallazgoInvalidosException si la lista está vacía, una clave es inválida o se repite
     */
    @Transactional
    public FichaPlantilla guardarVersion(String nombreCientifico, List<AtributoPlantilla> atributos, String autor) {
        if (nombreCientifico == null || nombreCientifico.isBlank()) {
            throw new DatosHallazgoInvalidosException("nombreCientifico", "obligatorio, maximo 150 caracteres");
        }
        validadorTexto.verificar("nombreCientifico", nombreCientifico);
        if (atributos == null || atributos.isEmpty()) {
            throw new DatosHallazgoInvalidosException("atributos", "se requiere al menos un atributo");
        }
        if (atributos.size() > MAX_ATRIBUTOS) {
            throw new DatosHallazgoInvalidosException("atributos",
                    "maximo " + MAX_ATRIBUTOS + " atributos por plantilla");
        }
        validarAtributos(atributos);

        String especie = nombreCientifico.strip();
        int siguienteVersion = plantillas.findFirstByNombreCientificoOrderByVersionDesc(especie)
                .map(p -> p.getVersion() + 1)
                .orElse(1);
        return plantillas.save(new FichaPlantilla(especie, siguienteVersion, atributos, autor.strip()));
    }

    /** Plantilla vigente de una especie, si tiene. */
    @Transactional(readOnly = true)
    public Optional<FichaPlantilla> vigente(String nombreCientifico) {
        return plantillas.findFirstByNombreCientificoOrderByVersionDesc(nombreCientifico.strip());
    }

    /** Plantilla vigente o error si la especie no tiene ninguna configurada. */
    @Transactional(readOnly = true)
    public FichaPlantilla exigirVigente(String nombreCientifico) {
        return vigente(nombreCientifico).orElseThrow(() -> new PlantillaNoEncontradaException(nombreCientifico));
    }

    /**
     * Comprueba los valores capturados contra la plantilla vigente de la especie (HU-02, criterio 2):
     * exige los obligatorios, rechaza claves desconocidas y verifica el tipo de cada valor.
     *
     * <p>Si la especie no tiene plantilla, no se exige nada: la captura de atributos es opcional.
     *
     * @throws DatosHallazgoInvalidosException si falta un obligatorio o un valor no corresponde al tipo
     */
    @Transactional(readOnly = true)
    public void validarValores(String nombreCientifico, Map<String, Object> valores) {
        Optional<FichaPlantilla> plantilla = vigente(nombreCientifico);
        if (plantilla.isEmpty()) {
            if (valores != null && !valores.isEmpty()) {
                throw new DatosHallazgoInvalidosException("atributos",
                        "la especie no tiene plantilla configurada");
            }
            return;
        }
        List<AtributoPlantilla> habilitados = plantilla.get().getAtributosHabilitados();
        Map<String, Object> recibidos = valores == null ? Map.of() : valores;

        Set<String> conocidos = new HashSet<>(habilitados.stream().map(AtributoPlantilla::clave).toList());
        for (String clave : recibidos.keySet()) {
            if (!conocidos.contains(clave)) {
                throw new DatosHallazgoInvalidosException("atributos." + clave, "atributo no definido en la plantilla");
            }
        }
        for (AtributoPlantilla atributo : habilitados) {
            Object valor = recibidos.get(atributo.clave());
            if (valor == null) {
                if (atributo.obligatorio()) {
                    throw new DatosHallazgoInvalidosException("atributos." + atributo.clave(), "obligatorio");
                }
                continue;
            }
            validarTipo(atributo, valor);
        }
    }

    private void validarAtributos(List<AtributoPlantilla> atributos) {
        Set<String> vistas = new HashSet<>();
        for (AtributoPlantilla atributo : atributos) {
            if (atributo.clave() == null || !CLAVE_VALIDA.matcher(atributo.clave()).matches()) {
                throw new DatosHallazgoInvalidosException("atributos",
                        "clave invalida: use minusculas, numeros y guion bajo");
            }
            if (!vistas.add(atributo.clave())) {
                throw new DatosHallazgoInvalidosException("atributos", "clave repetida: " + atributo.clave());
            }
            if (atributo.etiqueta() == null || atributo.etiqueta().isBlank()) {
                throw new DatosHallazgoInvalidosException("atributos." + atributo.clave(), "etiqueta obligatoria");
            }
            if (atributo.tipo() == null) {
                throw new DatosHallazgoInvalidosException("atributos." + atributo.clave(), "tipo obligatorio");
            }
            // La etiqueta y la unidad se muestran en formularios y fichas: tampoco pueden traer coordenadas.
            validadorTexto.verificar("atributos." + atributo.clave(), atributo.etiqueta());
            validadorTexto.verificar("atributos." + atributo.clave(), atributo.unidad());
        }
    }

    private static void validarTipo(AtributoPlantilla atributo, Object valor) {
        String campo = "atributos." + atributo.clave();
        boolean valido = switch (atributo.tipo()) {
            case TEXTO -> valor instanceof String;
            case NUMERO -> valor instanceof Number;
            case ENTERO -> esEntero(valor);
            case BOOLEANO -> valor instanceof Boolean;
            case FECHA -> esFecha(valor);
        };
        if (!valido) {
            throw new DatosHallazgoInvalidosException(campo, "se esperaba " + nombreLegible(atributo.tipo()));
        }
    }

    private static boolean esEntero(Object valor) {
        return (valor instanceof Integer || valor instanceof Long)
                || (valor instanceof Number numero && numero.doubleValue() == Math.floor(numero.doubleValue()));
    }

    private static boolean esFecha(Object valor) {
        if (!(valor instanceof String texto)) {
            return false;
        }
        try {
            LocalDate.parse(texto);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    private static String nombreLegible(TipoAtributo tipo) {
        return switch (tipo) {
            case TEXTO -> "un texto";
            case NUMERO -> "un numero";
            case ENTERO -> "un numero entero";
            case BOOLEANO -> "verdadero o falso";
            case FECHA -> "una fecha AAAA-MM-DD";
        };
    }
}
