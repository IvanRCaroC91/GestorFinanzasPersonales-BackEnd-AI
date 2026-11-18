package com.smartkash.finanzas;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Comprueba que el servicio arranca: que Spring construye todas sus piezas sin errores.
 *
 * <p>El cliente de Eureka se desactiva para que la prueba no intente conectarse a un servidor
 * que no existe, y se pasan credenciales inventadas porque la configuración las exige.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "EUREKA_USUARIO=usuario-de-prueba",
        "EUREKA_CONTRASENA=contrasena-de-prueba"
})
class FinanzasApplicationTests {

    @Test
    void contextLoads() {
    }
}