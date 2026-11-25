package com.smartkash.finanzas;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura del servicio de finanzas.
 *
 * <p>Qué hace: ArchUnit lee las clases compiladas del servicio (también las de prueba) y
 * comprueba que cada paquete solo depende de lo que le corresponde. Si alguien rompe una regla,
 * esta prueba falla. Así la arquitectura se protege sola y no depende de la memoria de quien
 * programa.</p>
 *
 * <p>Ojo: ArchUnit mira el código compilado, no el texto. Un {@code import} que no se usa
 * desaparece al compilar, así que no rompe ninguna regla; lo que cuenta es usar la clase.</p>
 */
@AnalyzeClasses(packages = "com.smartkash.finanzas")
class ArquitecturaTest {

    /** Paquetes de frameworks que las reglas de negocio no deben conocer. */
    private static final String[] FRAMEWORKS = {
            "org.springframework..", "jakarta.persistence..",
            "com.fasterxml.jackson..", "tools.jackson.."};

    private static final String PAQUETE_PERSISTENCIA_CUENTAS =
            "com.smartkash.finanzas.cuentas.adaptadores.persistencia";

    private static final String PAQUETE_SEGURIDAD = "com.smartkash.finanzas.seguridad..";
    private static final String PAQUETE_CUENTAS = "com.smartkash.finanzas.cuentas..";

    /**
     * El dominio (las reglas de negocio) debe ser Java puro: nada de Spring, de JPA ni de la
     * librería de JSON, en ninguna de sus dos versiones de paquete.
     *
     * <p>Por qué: si las reglas dependen de un framework, ya no se pueden probar de forma rápida
     * y aislada, y cambiar de tecnología obligaría a reescribirlas.</p>
     */
    @ArchTest
    static final ArchRule dominio_no_depende_de_frameworks =
            noClasses().that().resideInAPackage("..dominio..")
                    .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS);

    /**
     * Los casos de uso tampoco dependen de frameworks. Spring los crea desde fuera, con clases de
     * configuración en los adaptadores, sin que ellos lo sepan; por eso se prueban con un
     * repositorio falso y sin arrancar nada.
     */
    @ArchTest
    static final ArchRule aplicacion_no_depende_de_frameworks =
            noClasses().that().resideInAPackage("..aplicacion..")
                    .should().dependOnClassesThat().resideInAnyPackage(FRAMEWORKS);

    /**
     * La entidad JPA y la interfaz de Spring Data son detalles de cómo se guardan las cuentas.
     * Fuera del paquete de persistencia nadie debe usarlas: el resto del servicio pide las
     * cuentas por el puerto y recibe cuentas del negocio.
     *
     * <p>Por qué, si ya son privadas al paquete: hoy el compilador lo impide, pero bastaría con
     * añadir {@code public} para abrir la puerta. Esta regla la mantiene cerrada aunque eso
     * ocurra.</p>
     */
    @ArchTest
    static final ArchRule solo_la_persistencia_usa_la_entidad_y_spring_data =
            noClasses().that().resideOutsideOfPackage(PAQUETE_PERSISTENCIA_CUENTAS)
                    .should().dependOnClassesThat(
                            // name() compara el nombre completo de la clase, con su paquete.
                            name(PAQUETE_PERSISTENCIA_CUENTAS + ".CuentaEntidad")
                                    .or(name(PAQUETE_PERSISTENCIA_CUENTAS + ".CuentasSpringData")));

    /**
     * Las reglas de negocio no conocen a quien las usa: el dominio y los casos de uso no
     * dependen de los adaptadores (web, persistencia) ni de la configuración de seguridad.
     *
     * <p>Por qué: las dependencias van de fuera hacia dentro. Así se puede cambiar la forma de
     * exponer o guardar los datos sin tocar el negocio.</p>
     */
    @ArchTest
    static final ArchRule el_negocio_no_conoce_los_adaptadores =
            noClasses().that().resideInAnyPackage("..dominio..", "..aplicacion..")
                    .should().dependOnClassesThat().resideInAnyPackage("..adaptadores..", PAQUETE_SEGURIDAD);

    /**
     * La capa web no lee la base de datos por su cuenta: pide los datos al caso de uso. Así las
     * reglas (por ejemplo, que cada persona solo vea lo suyo) no se pueden saltar desde un
     * controlador.
     */
    @ArchTest
    static final ArchRule la_web_no_usa_la_persistencia =
            noClasses().that().resideInAPackage("..adaptadores.web..")
                    .should().dependOnClassesThat().resideInAPackage("..adaptadores.persistencia..");

    /**
     * Las rutas HTTP solo se publican desde los adaptadores web. Una ruta en otro sitio quedaría
     * fuera de la vista de quien revisa qué expone el servicio.
     *
     * <p>Solo cuenta las clases de primer nivel: una ruta anidada dentro de una clase de
     * prueba, que solo existe en el arranque de esa prueba, no forma parte del servicio.</p>
     */
    @ArchTest
    static final ArchRule las_rutas_solo_se_publican_en_los_adaptadores_web =
            classes().that().areAnnotatedWith(RestController.class).and().areTopLevelClasses()
                    .should().resideInAPackage("..adaptadores.web..");

    /**
     * La seguridad es común a todo el servicio y no depende de ninguna funcionalidad. Así sirve
     * igual para las cuentas que para las que vengan después.
     */
    @ArchTest
    static final ArchRule la_seguridad_no_depende_de_las_funcionalidades =
            noClasses().that().resideInAPackage(PAQUETE_SEGURIDAD)
                    .should().dependOnClassesThat().resideInAPackage(PAQUETE_CUENTAS);

    /**
     * Las funcionalidades tampoco dependen de las clases de seguridad del servicio: reciben el
     * token ya validado a través de Spring Security. Así se puede cambiar cómo se valida sin
     * tocar los controladores.
     */
    @ArchTest
    static final ArchRule las_funcionalidades_no_dependen_de_la_seguridad =
            noClasses().that().resideInAPackage(PAQUETE_CUENTAS)
                    .should().dependOnClassesThat().resideInAPackage(PAQUETE_SEGURIDAD);
}
