package com.smartkash.finanzas;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
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
}
