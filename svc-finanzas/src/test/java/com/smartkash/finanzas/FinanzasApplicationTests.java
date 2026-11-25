package com.smartkash.finanzas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Comprueba que el servicio completo arranca: que Spring construye todas sus piezas sin errores.
 *
 * <p>Qué cubre: al arrancar, Flyway aplica las migraciones contra el PostgreSQL desechable de
 * {@link PruebaConContenedores} y Hibernate comprueba que las clases del código encajan con las
 * tablas. Si falta la base de datos, una migración tiene un error o una entidad no encaja con su
 * tabla, el servicio no arranca y esta prueba falla.</p>
 *
 * <p>De dónde sale la configuración: la conexión a la base la pone el contenedor y la
 * desactivación de Eureka la pone {@link EntornoDePruebas}. Por eso aquí no se escribe ninguna
 * propiedad: así esta clase comparte el mismo servicio arrancado que las demás pruebas, en lugar
 * de obligar a Spring a levantar otro con una configuración distinta.</p>
 *
 * <p>Necesita Docker abierto.</p>
 */
@SpringBootTest
class FinanzasApplicationTests extends PruebaConContenedores {

    /** El servicio arranca contra un PostgreSQL real sin lanzar ningún error. */
    @Test
    void el_servicio_arranca_contra_postgresql() {
        // Vacía a propósito: si el contexto no arrancara, la prueba fallaría antes de llegar aquí.
    }
}
