package co.edu.unbosque.biohub.cientifico.ubicacion;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.assignableTo;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleName;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameStartingWith;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.core.domain.properties.HasOwner.Predicates.With.owner;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import co.edu.unbosque.biohub.cientifico.ubicacion.domain.CifradorCoordenadas;
import co.edu.unbosque.biohub.cientifico.ubicacion.domain.RepositorioUbicaciones;
import co.edu.unbosque.biohub.cientifico.ubicacion.service.PoliticaAccesoUbicacion;

/** ARQ-18 (SAD §7.1): las dependencias apuntan hacia domain y controller no lo importa nadie de abajo. */
class ArquitecturaUbicacionTest {

    private static final String BASE = "co.edu.unbosque.biohub.cientifico.ubicacion";
    private static final String INFRA = BASE + ".infrastructure"; // paquete exacto, sin ".." (no casa con registro.ubicacion.infrastructure)
    private static final String RECIFRADO = BASE + ".service.RecifradoService";
    private static final String SERVICIO_DESCIFRADO = BASE + ".service.ServicioDescifrado";
    private static final String PROVEEDOR_LLAVES = BASE + ".domain.ProveedorDeLlaves";

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("co.edu.unbosque.biohub.cientifico");

    @Test
    @DisplayName("ARQ-18: ubicacion.service no depende de ubicacion.controller")
    void serviceNoImportaController() {
        noClasses().that().resideInAPackage("..ubicacion.service..")
                .should().dependOnClassesThat().resideInAPackage("..ubicacion.controller..").check(CLASES);
    }

    @Test
    @DisplayName("ARQ-18: ubicacion.infrastructure y ubicacion.domain no dependen de ubicacion.controller")
    void infrastructureYDomainNoImportanController() {
        noClasses().that().resideInAnyPackage("..ubicacion.infrastructure..", "..ubicacion.domain..")
                .should().dependOnClassesThat().resideInAPackage("..ubicacion.controller..").check(CLASES);
    }

    // ---- ARQ-28 (SAD §7.1): las dependencias apuntan hacia domain ----

    @Test
    @DisplayName("ARQ-28: layeredArchitecture; domain no depende de nadie, infrastructure no depende de service ni de controller")
    void arquitecturaEnCapas() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy("..ubicacion.controller..")
                .layer("Service").definedBy("..ubicacion.service..")
                .layer("Infrastructure").definedBy("..ubicacion.infrastructure..")
                .layer("Domain").definedBy("..ubicacion.domain..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller")
                .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
                .check(CLASES);
    }

    @Test
    @DisplayName("ARQ-28: domain no depende de service, infrastructure ni controller")
    void domainNoDependeDeLasCapasExternas() {
        noClasses().that().resideInAPackage("..ubicacion.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..ubicacion.service..", "..ubicacion.infrastructure..",
                        "..ubicacion.controller..")
                .check(CLASES);
    }

    @Test
    @DisplayName("ARQ-28: infrastructure no depende de service (la auditoria es un puerto de domain)")
    void infrastructureNoDependeDeService() {
        noClasses().that().resideInAPackage("..ubicacion.infrastructure..")
                .should().dependOnClassesThat().resideInAPackage("..ubicacion.service..").check(CLASES);
    }

    // ---- D43 / ARQ-27 / SEC-J-10: la cadena del Decorator no se puede esquivar desde ningún módulo del servicio ----

    @Test
    @DisplayName("D43 ARQ-10: nadie fuera de ubicacion.infrastructure usa UbicacionHallazgoRepository (todo el servicio, también registro)")
    void soloInfrastructureUsaElRepositorioSpringData() {
        noClasses().that().resideOutsideOfPackages("..ubicacion.infrastructure..", "..ubicacion.domain..")
                .should().dependOnClassesThat().haveSimpleName("UbicacionHallazgoRepository").check(CLASES);
    }

    @Test
    @DisplayName("D43 ARQ-27: solo la infraestructura de ubicacion y RecifradoService dependen del EntityManager dentro de ubicacion")
    void entityManagerSoloEnInfraestructuraYRecifrado() {
        noClasses().that().resideInAPackage("..ubicacion..")
                .and().resideOutsideOfPackage(INFRA)
                .and().doNotHaveFullyQualifiedName(RECIFRADO)
                .should().dependOnClassesThat().areAssignableTo(jakarta.persistence.EntityManager.class).check(CLASES);
    }

    @Test
    @DisplayName("D43 SEC-J-10: CifradorCoordenadas solo lo usan la infraestructura de ubicacion y RecifradoService (todo el servicio)")
    void cifradorSoloEnInfraestructuraYRecifrado() {
        noClasses().that().resideOutsideOfPackage(INFRA)
                .and().doNotHaveFullyQualifiedName(RECIFRADO)
                .and().doNotHaveFullyQualifiedName(CifradorCoordenadas.class.getName()) // la propia interfaz del puerto
                .should().dependOnClassesThat().areAssignableTo(CifradorCoordenadas.class).check(CLASES);
    }

    @Test
    @DisplayName("D43 SEC-J-10: solo ServicioDescifrado (que exige el rol) y los decoradores llaman a obtenerUbicacionExacta")
    void soloServicioDescifradoPideCoordenadasExactas() {
        // accessTargetWhere cubre llamadas y referencias a método (repo::obtenerUbicacionExacta)
        noClasses().that().resideOutsideOfPackage(INFRA)
                .and().doNotHaveFullyQualifiedName(SERVICIO_DESCIFRADO)
                .should().accessTargetWhere(target(name("obtenerUbicacionExacta"))
                        .and(target(owner(assignableTo(RepositorioUbicaciones.class)))))
                .check(CLASES);
    }

    @Test
    @DisplayName("D43 SEC-J-16: solo ubicacion.infrastructure (paquete exacto) y RecifradoService dependen de ProveedorDeLlaves y PropiedadesCifrado")
    void llavesSoloEnInfraestructuraYRecifrado() {
        // ProveedorDeLlaves (puerto en domain) se excluye a sí mismo; no hay otros usos legítimos (la configuración vive en infrastructure).
        noClasses().that().resideOutsideOfPackage(INFRA)
                .and().doNotHaveFullyQualifiedName(RECIFRADO)
                .and().doNotHaveFullyQualifiedName(PROVEEDOR_LLAVES)
                .should().dependOnClassesThat().haveFullyQualifiedName(PROVEEDOR_LLAVES)
                .orShould().dependOnClassesThat().haveFullyQualifiedName(INFRA + ".PropiedadesCifrado")
                .check(CLASES);
    }

    @Test
    @DisplayName("D43 SEC-J-10: ServicioDescifrado exige el rol (PoliticaAccesoUbicacion.exigirCoordenadas) antes de descifrar")
    void servicioDescifradoExigeElRol() {
        classes().that().haveSimpleName("ServicioDescifrado")
                .should().callMethod(PoliticaAccesoUbicacion.class, "exigirCoordenadas", Authentication.class)
                .check(CLASES);
    }

    @Test
    @DisplayName("D43 SEC-J-10: los eslabones de la cadena (decoradores y JPA) son privados del paquete y sus constructores también")
    void eslabonesDeLaCadenaSonPrivadosDelPaquete() {
        classes().that().haveSimpleNameStartingWith("Decorador").and().resideInAPackage("..ubicacion.infrastructure..")
                .or().haveSimpleName("RepositorioUbicacionesJpa")
                .should().bePackagePrivate().check(CLASES);
        constructors().that().areDeclaredInClassesThat(
                        resideInAPackage("..ubicacion.infrastructure..").and(simpleNameStartingWith("Decorador"))
                                .or(simpleName("RepositorioUbicacionesJpa")))
                .should().bePackagePrivate().orShould().beProtected().check(CLASES);
    }

    @Test
    @DisplayName("D43 SEC-J-10: solo ConfiguracionUbicacion instancia los eslabones de la cadena")
    void soloLaConfiguracionArmaLaCadena() {
        noClasses().that().doNotHaveSimpleName("ConfiguracionUbicacion")
                .and().haveSimpleNameNotStartingWith("Decorador") // los decoradores llaman a super(envuelto)
                .should().callConstructorWhere(new DescribedPredicate<JavaConstructorCall>(
                        "el destino es un decorador o RepositorioUbicacionesJpa") {
                    @Override
                    public boolean test(JavaConstructorCall llamada) {
                        String nombre = llamada.getTargetOwner().getSimpleName();
                        return nombre.startsWith("Decorador") || nombre.equals("RepositorioUbicacionesJpa");
                    }
                })
                .check(CLASES);
    }

    @Test
    @DisplayName("ARQ-09 RF13: solo VisibilidadExacta y la política conocen a ServicioDescifrado (todo el servicio)")
    void soloLaEstrategiaExactaUsaElServicioDeDescifrado() {
        noClasses().that().doNotHaveSimpleName("VisibilidadExacta").and().doNotHaveSimpleName("PoliticaAccesoUbicacion")
                .and().doNotHaveSimpleName("ServicioDescifrado")
                .should().dependOnClassesThat().haveSimpleName("ServicioDescifrado").check(CLASES);
    }

    @Test
    @DisplayName("ARQ-08: el módulo eventos no depende de ubicacion ni de registro (Observer desacoplado)")
    void eventosNoDependeDeLosModulos() {
        noClasses().that().resideInAPackage("..cientifico.eventos.domain..")
                .should().dependOnClassesThat().resideInAnyPackage("..cientifico.ubicacion..", "..cientifico.registro..").check(CLASES);
    }
}
