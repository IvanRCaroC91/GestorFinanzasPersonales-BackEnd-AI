package com.smartkash.identidad;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Reglas de arquitectura del servicio de identidad.
 *
 * <p>Qué hace: ArchUnit lee las clases compiladas del servicio y comprueba que respetan
 * la estructura acordada. Si alguien rompe una regla, esta prueba falla y el cambio no
 * puede fusionarse. Así la arquitectura se protege sola y no depende de la memoria de
 * quien programa.</p>
 */
@AnalyzeClasses(packages = "com.smartkash.identidad")
class ArquitecturaTest {

    /**
     * El dominio (las reglas de negocio) debe ser Java puro.
     *
     * <p>Por qué: si las reglas de negocio dependen de Spring, de la base de datos o de
     * la librería de JSON, ya no se pueden probar de forma rápida y aislada, y cambiar
     * de tecnología obligaría a reescribirlas.</p>
     *
     * <p>{@code allowEmptyShould(true)} permite que la regla pase mientras todavía no
     * exista ninguna clase en un paquete {@code dominio}; sin eso, fallaría por no
     * encontrar nada que revisar.</p>
     */
    @ArchTest
    static final ArchRule dominio_no_depende_de_frameworks =
            noClasses().that().resideInAPackage("..dominio..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta.persistence..",
                            "com.fasterxml.jackson..")
                    .allowEmptyShould(true);

    /**
     * El caso de uso del inicio de sesión tampoco depende de frameworks.
     *
     * <p>Por qué: coordina reglas de seguridad delicadas (bloqueos, comparación de
     * contraseñas, emisión de tokens) y se prueba entero con puertos falsos, sin arrancar
     * Spring. Spring lo crea desde fuera, con una clase de configuración en los
     * adaptadores, sin que el caso de uso lo sepa.</p>
     */
    @ArchTest
    static final ArchRule aplicacion_de_la_sesion_no_depende_de_frameworks =
            noClasses().that().resideInAPackage("com.smartkash.identidad.sesion.aplicacion..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..", "jakarta.persistence..",
                            "com.fasterxml.jackson..");

    /**
     * El paquete compartido de cuentas no puede depender de las funcionalidades que lo usan.
     *
     * <p>Por qué: {@code cuenta} reúne lo que comparten el registro, el inicio de sesión y
     * la verificación del correo. La dependencia va en un solo sentido: ellas conocen a
     * {@code cuenta}, pero {@code cuenta} no las conoce. Si las conociera, cambiar una
     * funcionalidad podría romper las otras dos a través del paquete compartido, y ya no se
     * podría entender ni probar cada una por separado.</p>
     *
     * <p>La regla revisa también las clases de prueba del paquete.</p>
     */
    @ArchTest
    static final ArchRule cuenta_no_depende_de_las_funcionalidades =
            noClasses().that().resideInAPackage("com.smartkash.identidad.cuenta..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.smartkash.identidad.registro..",
                            "com.smartkash.identidad.sesion..",
                            "com.smartkash.identidad.verificacion..");

    /**
     * El paquete compartido de tokens tampoco puede depender de las funcionalidades que lo
     * usan.
     *
     * <p>Por qué: generar valores aleatorios y calcular huellas lo necesitan tanto el inicio
     * de sesión como la verificación del correo. Igual que con las cuentas, la dependencia
     * va en un solo sentido.</p>
     */
    @ArchTest
    static final ArchRule tokens_no_depende_de_las_funcionalidades =
            noClasses().that().resideInAPackage("com.smartkash.identidad.tokens..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.smartkash.identidad.registro..",
                            "com.smartkash.identidad.sesion..",
                            "com.smartkash.identidad.verificacion..");
}
