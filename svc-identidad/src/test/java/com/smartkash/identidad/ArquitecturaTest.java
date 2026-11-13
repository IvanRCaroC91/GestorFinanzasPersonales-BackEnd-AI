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
}
