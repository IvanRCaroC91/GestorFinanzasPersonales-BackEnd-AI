package com.smartkash.finanzas;

import com.netflix.discovery.EurekaClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que las pruebas del servicio no intentan registrarse en Eureka.
 *
 * <p>El cliente de Eureka es un bean que Spring solo crea cuando el cliente está activado. Las
 * pruebas lo desactivan en {@link EntornoDePruebas}; si alguien quita esa propiedad, el bean
 * aparece y esta prueba falla.</p>
 */
@SpringBootTest
class DescubrimientoEnPruebasTest extends PruebaConContenedores {

    @Autowired
    private ApplicationContext contexto;

    /** El servicio completo arranca sin ningún cliente de Eureka. */
    @Test
    void el_cliente_de_eureka_no_existe_en_las_pruebas() {
        assertThat(contexto.getBeanNamesForType(EurekaClient.class)).isEmpty();
    }
}