package co.edu.unbosque.biohub.consultapublica;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.EvaluationResult;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import co.edu.unbosque.biohub.sondaseguridad.SondasElusion;

/**
 * 11-seguridad-y-acceso.md §3: en servicio-consulta-publica la URL termina en {@code denyAll()} y cada ruta
 * permitida tiene su matcher explícito en {@code SecurityConfig}; además, cada endpoint declara su control en un
 * {@code @PreAuthorize}. Esta prueba impide que un endpoint nuevo llegue sin él o con uno trivial (SEC-J-19, mismas
 * reglas que servicio-cientifico). Escanea todo el paquete base ({@code ...biohub.consultapublica}) sin las clases
 * de test, y aplica estas reglas:
 * <ol>
 *   <li><b>R1</b> Toda clase con estereotipo de controlador ({@code @Controller} o un estereotipo propio
 *       meta-anotado con él) debe ser {@code @RestController} directo: se cierra {@code @Controller} +
 *       {@code @ResponseBody}, y los estereotipos propios.</li>
 *   <li><b>R2</b> Todo método con mapping ({@code @GetMapping}, {@code @PostMapping}, {@code @PutMapping},
 *       {@code @PatchMapping}, {@code @DeleteMapping}, {@code @RequestMapping}) se declara en una clase concreta
 *       anotada {@code @RestController}: se cierran las interfaces, las clases base abstractas y las clases con
 *       {@code @RequestMapping} de tipo que Spring trata como handler sin ser controllers.</li>
 *   <li><b>R3</b> Un {@code @RestController} no hereda ni implementa métodos con mapping (aunque la superclase o la
 *       interfaz esté fuera de los paquetes escaneados): los métodos efectivos del controlador son los que él
 *       declara.</li>
 *   <li><b>R4</b> Todo método con mapping tiene {@code @PreAuthorize} <b>con un control de rol explícito</b>. Lista
 *       blanca (la expresión completa debe encajar, sin espacios sobrantes, sin negaciones ni paréntesis):
 *       <ul>
 *         <li>{@code hasRole('r')} y {@code hasAnyRole('r1', 'r2', ...)}, con {@code r} uno de los 5 roles del realm
 *             ({@link #ROLES});</li>
 *         <li>varios de los anteriores unidos con {@code or}, {@code and}, {@code ||} o {@code &&};</li>
 *         <li>{@code permitAll()} solo si el método está en {@link #PUBLICOS_PERMIT_ALL}: las tres rutas públicas
 *             ({@code GET /api/publica/ping}, {@code /fichas} y {@code /fichas/{id}}). Un endpoint público nuevo exige
 *             añadirlo a esa lista (y su matcher en {@code SecurityConfig}), de modo que alguien lo revise.</li>
 *       </ul>
 *       Se rechazan, entre otros, {@code isAuthenticated()}, {@code true}, {@code permitAll()} fuera de la lista,
 *       {@code denyAll()}, la expresión vacía, {@code hasAuthority(...)}, {@code hasRole('x') or permitAll()} y
 *       {@code hasRole('x') or 1 == 1}. Un {@code @PreAuthorize} solo a nivel de clase o heredado por una
 *       meta-anotación propia tampoco cuenta: se escribe en cada método (más estricto, a propósito).</li>
 *   <li><b>R5</b> Ninguna clase usa rutas funcionales ({@code org.springframework.web.servlet.function} o
 *       {@code reactive.function}: {@code RouterFunction}, {@code RouterFunctions}, {@code HandlerFunction}).</li>
 *   <li><b>R6</b> Ninguna clase es un handler alternativo sin {@code @PreAuthorize}: implementar
 *       {@code org.springframework.web.servlet.mvc.Controller} o {@code HttpRequestHandler}, ser un
 *       {@code jakarta.servlet.Servlet} o registrar uno con {@code ServletRegistrationBean}.</li>
 * </ol>
 * Las sondas de {@code co.edu.unbosque.biohub.sondaseguridad.SondasElusion} (solo en src/test, fuera del paquete
 * escaneado) demuestran que cada vía de elusión es detectada por la regla indicada.
 */
class ArquitecturaSeguridadTest {

    /** Los 5 roles internos del realm (11-seguridad-y-acceso.md §2). */
    static final Set<String> ROLES = Set.of("investigador_campo", "auditor_cientifico", "cientifico_alto_rango",
            "analista_ambiental", "administrador");

    /** Métodos que pueden declarar {@code permitAll()}, como {@code Clase.metodo}: las rutas públicas de HU-01. */
    static final Set<String> PUBLICOS_PERMIT_ALL = Set.of("PingController.ping", "FichasPublicasController.buscar",
            "FichasPublicasController.ficha");

    private static final String ROL_LITERAL = "'(?:" + String.join("|", ROLES) + ")'";
    private static final String TERMINO = "(?:hasRole\\(\\s*" + ROL_LITERAL + "\\s*\\)"
            + "|hasAnyRole\\(\\s*" + ROL_LITERAL + "(?:\\s*,\\s*" + ROL_LITERAL + ")*\\s*\\))";
    private static final Pattern EXPRESION_CON_ROL =
            Pattern.compile(TERMINO + "(?:\\s+(?:or|and|\\|\\||&&)\\s+" + TERMINO + ")*");

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("co.edu.unbosque.biohub.consultapublica");

    // GetMapping, PostMapping, etc. llevan @RequestMapping como meta-anotación
    private static final DescribedPredicate<CanBeAnnotated> ES_ENDPOINT =
            CanBeAnnotated.Predicates.metaAnnotatedWith(RequestMapping.class)
                    .or(CanBeAnnotated.Predicates.annotatedWith(RequestMapping.class));

    // ---- lógica de la lista blanca (probada aparte) ----

    static boolean expresionAceptada(String expresion, boolean metodoPublico) {
        if (expresion == null || expresion.isBlank()) {
            return false;
        }
        String e = expresion.strip();
        if (metodoPublico && e.equals("permitAll()")) {
            return true;
        }
        return EXPRESION_CON_ROL.matcher(e).matches();
    }

    // ---- reglas ----

    static ArchRule r1ControladoresSonRestController() {
        return classes().that().areMetaAnnotatedWith(Controller.class)
                .should().beAnnotatedWith(RestController.class)
                .because("solo se admite @RestController directo (no @Controller ni estereotipos propios)")
                .allowEmptyShould(true);
    }

    static ArchRule r2MappingsSoloEnRestControllerConcreto() {
        return methods().that(ES_ENDPOINT)
                .should().beDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .andShould().beDeclaredInClassesThat().areNotInterfaces()
                .andShould().beDeclaredInClassesThat().doNotHaveModifier(JavaModifier.ABSTRACT)
                .because("los mappings se declaran en la clase concreta del @RestController, no en interfaces, "
                        + "clases base ni clases con solo @RequestMapping")
                .allowEmptyShould(true);
    }

    static ArchRule r3RestControllerNoHeredaMappings() {
        return classes().that().areAnnotatedWith(RestController.class)
                .should(new ArchCondition<JavaClass>("no heredar ni implementar métodos con mapping") {
                    @Override
                    public void check(JavaClass controlador, ConditionEvents eventos) {
                        Stream.concat(controlador.getAllRawSuperclasses().stream(),
                                        controlador.getAllRawInterfaces().stream())
                                .filter(padre -> !padre.isEquivalentTo(Object.class))
                                .forEach(padre -> padre.getMethods().stream()
                                        .filter(ES_ENDPOINT::test)
                                        .forEach(m -> eventos.add(SimpleConditionEvent.violated(controlador,
                                                controlador.getName() + " hereda el mapping " + m.getFullName()))));
                    }
                })
                .because("los métodos efectivos del controlador son los que él declara")
                .allowEmptyShould(true);
    }

    static ArchRule r4EndpointsConRolExplicito() {
        return methods().that(ES_ENDPOINT)
                .should(new ArchCondition<JavaMethod>("tener @PreAuthorize con un rol explícito de la lista blanca") {
                    @Override
                    public void check(JavaMethod metodo, ConditionEvents eventos) {
                        String expresion = metodo.tryGetAnnotationOfType(PreAuthorize.class)
                                .map(PreAuthorize::value).orElse(null);
                        String id = metodo.getOwner().getSimpleName() + "." + metodo.getName();
                        if (!expresionAceptada(expresion, PUBLICOS_PERMIT_ALL.contains(id))) {
                            eventos.add(SimpleConditionEvent.violated(metodo, metodo.getFullName()
                                    + ": falta @PreAuthorize o su expresión no es un control de rol de la lista blanca ("
                                    + (expresion == null ? "sin anotación" : "'" + expresion + "'") + ")"));
                        }
                    }
                })
                .because("cada endpoint debe declarar su rol: hasRole/hasAnyRole, o permitAll() solo en las rutas públicas listadas (SEC-J-19)")
                .allowEmptyShould(true);
    }

    static ArchRule r5SinRutasFuncionales() {
        return noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web.servlet.function..",
                        "org.springframework.web.reactive.function..")
                .because("las rutas funcionales (RouterFunction) no pasan por @PreAuthorize")
                .allowEmptyShould(true);
    }

    static ArchRule r6SinHandlersAlternativos() {
        return noClasses().should().beAssignableTo(org.springframework.web.servlet.mvc.Controller.class)
                .orShould().beAssignableTo(HttpRequestHandler.class)
                .orShould().beAssignableTo(jakarta.servlet.Servlet.class)
                .orShould().dependOnClassesThat().areAssignableTo(ServletRegistrationBean.class)
                .because("los handlers por nombre de bean y los servlets propios no pasan por @PreAuthorize")
                .allowEmptyShould(true);
    }

    static Map<String, ArchRule> reglas() {
        Map<String, ArchRule> r = new LinkedHashMap<>();
        r.put("R1", r1ControladoresSonRestController());
        r.put("R2", r2MappingsSoloEnRestControllerConcreto());
        r.put("R3", r3RestControllerNoHeredaMappings());
        r.put("R4", r4EndpointsConRolExplicito());
        r.put("R5", r5SinRutasFuncionales());
        r.put("R6", r6SinHandlersAlternativos());
        return r;
    }

    // ---- código de producción ----

    static Stream<String> nombresDeRegla() {
        return reglas().keySet().stream();
    }

    @ParameterizedTest(name = "SEG {0}: el código de producción cumple la regla")
    @MethodSource("nombresDeRegla")
    @DisplayName("SEG: todo el paquete base cumple las reglas de endpoints con rol explícito")
    void elCodigoDeProduccionCumpleLasReglas(String nombre) {
        reglas().get(nombre).check(CLASES);
    }

    @Test
    @DisplayName("SEG: la regla escanea el paquete base completo y encuentra los endpoints existentes")
    void elEscaneoNoEstaVacio() {
        long endpoints = CLASES.stream().flatMap(c -> c.getMethods().stream()).filter(ES_ENDPOINT::test).count();
        assertThat(endpoints).as("endpoints con mapping encontrados en el paquete base").isGreaterThanOrEqualTo(4);
        assertThat(CLASES.stream().map(JavaClass::getPackageName))
                .anyMatch(p -> p.endsWith(".consultapublica.controller"))
                .anyMatch(p -> p.endsWith(".eventos.controller"));
    }

    // ---- lista blanca de expresiones ----

    @ParameterizedTest(name = "acepta: {0}")
    @ValueSource(strings = {
            "hasRole('administrador')",
            "hasRole( 'cientifico_alto_rango' )",
            "hasAnyRole('investigador_campo','auditor_cientifico','cientifico_alto_rango','analista_ambiental','administrador')",
            "hasAnyRole('investigador_campo', 'cientifico_alto_rango')",
            "hasRole('administrador') or hasRole('auditor_cientifico')",
            "hasRole('administrador') && hasRole('auditor_cientifico')",
            "  hasRole('administrador')  "})
    void laListaBlancaAceptaControlesDeRol(String expresion) {
        assertThat(expresionAceptada(expresion, false)).isTrue();
    }

    @ParameterizedTest(name = "rechaza: [{0}]")
    @ValueSource(strings = {
            "", "   ", "isAuthenticated()", "isFullyAuthenticated()", "permitAll()", "denyAll()", "true", "false",
            "isAnonymous()", "hasRole('administrador') or permitAll()", "hasRole('administrador') or isAuthenticated()",
            "hasRole('administrador') or true", "hasRole('administrador') or 1 == 1",
            "hasRole('administrador') or !hasRole('administrador')", "!hasRole('administrador')",
            "(hasRole('administrador'))", "hasRole('cualquiera')", "hasRole('ADMINISTRADOR')", "hasRole()",
            "hasAnyRole()", "hasAnyRole('administrador','cualquiera')", "hasRole(null)",
            "hasAuthority('ROLE_administrador')", "hasRole(\"administrador\")",
            "@politicaUbicacion.puedeVerCoordenadas(authentication)", "hasRole('administrador'); permitAll()",
            "hasRole('administrador') or" })
    void laListaBlancaRechazaExpresionesSinRolExplicito(String expresion) {
        assertThat(expresionAceptada(expresion, false)).isFalse();
    }

    @Test
    void lasRutasPublicasListadasExistenYSonLasUnicasConPermitAll() {
        // PUBLICOS_PERMIT_ALL no debe tener entradas huérfanas: cada una es un método real con mapping
        java.util.Set<String> existentes = CLASES.stream().flatMap(c -> c.getMethods().stream())
                .filter(ES_ENDPOINT::test).map(m -> m.getOwner().getSimpleName() + "." + m.getName())
                .collect(java.util.stream.Collectors.toSet());
        assertThat(existentes).containsAll(PUBLICOS_PERMIT_ALL);
    }

    @Test
    void laExpresionNulaSeRechazaYPermitAllSoloEnLaListaDePublicos() {
        assertThat(expresionAceptada(null, false)).isFalse();
        assertThat(expresionAceptada(null, true)).isFalse();
        assertThat(expresionAceptada("permitAll()", true)).isTrue();
        assertThat(expresionAceptada("isAuthenticated()", true)).isFalse();
        assertThat(expresionAceptada("hasRole('administrador') or permitAll()", true)).isFalse();
    }

    // ---- sondas: cada vía de elusión debe ser detectada ----

    /** Sonda, regla que debe fallar y texto que debe aparecer en el informe de violaciones. */
    private static Arguments sonda(Class<?> clase, String regla, String texto) {
        return Arguments.of(clase.getSimpleName(), clase, regla, texto);
    }

    static Stream<Arguments> sondasDeElusion() {
        return Stream.of(
                // SEC-J-19, tabla del reporte
                sonda(SondasElusion.ControllerConResponseBody.class, "R1", "ControllerConResponseBody"),
                sonda(SondasElusion.ControllerConResponseBody.class, "R2", "sinRol"),
                sonda(SondasElusion.ControllerConResponseBody.class, "R4", "sinRol"),
                sonda(SondasElusion.ControllerConResponseEntity.class, "R1", "ControllerConResponseEntity"),
                sonda(SondasElusion.ControllerConRolCorrecto.class, "R1", "ControllerConRolCorrecto"),
                sonda(SondasElusion.ConEstereotipoPropio.class, "R1", "ConEstereotipoPropio"),
                sonda(SondasElusion.ComponenteConRequestMapping.class, "R2", "componente"),
                sonda(SondasElusion.HeredaMappingDeClaseBase.class, "R3", "heredado"),
                sonda(SondasElusion.HeredaMappingConRol.class, "R3", "heredado"),
                sonda(SondasElusion.ImplementaInterfazConMapping.class, "R3", "interfaz"),
                sonda(SondasElusion.IsAuthenticated.class, "R4", "isAuthenticated()"),
                sonda(SondasElusion.PermitAllNoListado.class, "R4", "permitAll()"),
                sonda(SondasElusion.PreAuthorizeVacio.class, "R4", "vacio"),
                sonda(SondasElusion.PreAuthorizeTrivial.class, "R4", "'true'"),
                sonda(SondasElusion.RolMasPermitAll.class, "R4", "mixto"),
                sonda(SondasElusion.RolMasTautologia.class, "R4", "tautologia"),
                sonda(SondasElusion.RolInexistente.class, "R4", "rolRaro"),
                sonda(SondasElusion.RutasFuncionales.class, "R5", "RutasFuncionales"),
                sonda(SondasElusion.RutasFuncionalesOcultas.class, "R5", "RutasFuncionalesOcultas"),
                sonda(SondasElusion.ManejadorHttpRequestHandler.class, "R6", "ManejadorHttpRequestHandler"),
                sonda(SondasElusion.ServletPropio.class, "R6", "ServletPropio"));
    }

    @ParameterizedTest(name = "{0} es detectada por {2}")
    @MethodSource("sondasDeElusion")
    @DisplayName("SEG: cada vía de elusión de SEC-J-19 es detectada")
    void cadaViaDeElusionEsDetectada(String nombre, Class<?> sonda, String regla, String texto) {
        // solo la sonda: sus clases base e interfaces se resuelven desde el classpath sin importarse (caso de una
        // superclase fuera de los paquetes escaneados)
        JavaClasses fixture = new ClassFileImporter().importClasses(sonda);
        EvaluationResult resultado = reglas().get(regla).evaluate(fixture);
        assertThat(resultado.hasViolation()).as("%s debe violar %s", nombre, regla).isTrue();
        assertThat(resultado.getFailureReport().toString()).contains(texto);
    }

    @Test
    @DisplayName("SEG: la herencia de un mapping también la ve R2 cuando la clase base está en el escaneo")
    void laClaseBaseConMappingEsDetectadaPorR2() {
        JavaClasses fixture = new ClassFileImporter().importClasses(SondasElusion.BaseConMappingSinRol.class,
                SondasElusion.BaseConMappingYRol.class, SondasElusion.ApiGenerada.class);
        EvaluationResult resultado = reglas().get("R2").evaluate(fixture);
        assertThat(resultado.getFailureReport().toString())
                .contains("BaseConMappingSinRol", "BaseConMappingYRol", "ApiGenerada");
    }

    @Test
    @DisplayName("SEG: un controlador correcto no viola ninguna regla (las reglas no rechazan todo)")
    void unControladorCorrectoPasaTodasLasReglas() {
        JavaClasses fixture = new ClassFileImporter().importClasses(SondasElusion.ControladorCorrecto.class);
        reglas().forEach((nombre, regla) ->
                assertThat(regla.evaluate(fixture).hasViolation()).as("regla %s", nombre).isFalse());
    }
}
