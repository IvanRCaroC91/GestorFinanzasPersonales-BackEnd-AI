package com.smartkash.eureka;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Comprueba que el servidor de descubrimiento arranca.
 *
 * <p>El servidor exige usuario y contraseña y no tiene valores por defecto, así que la prueba
 * los entrega ella misma con valores inventados. De ese modo no depende del archivo {@code .env}
 * de nadie ni de variables del sistema.
 */
@SpringBootTest(properties = {
        "EUREKA_USUARIO=usuario-de-prueba",
        "EUREKA_CONTRASENA=contrasena-de-prueba"
})
class EurekaApplicationTests {

    @Test
    void contextLoads() {
    }

}
