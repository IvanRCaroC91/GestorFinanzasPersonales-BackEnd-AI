package com.smartkash.identidad;

import com.netflix.discovery.EurekaClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Comprueba que las pruebas del servicio no intentan registrarse en Eureka.
 *
 * <p>Por qué importa: el servicio real se anota en Eureka al arrancar. En las pruebas no hay
 * ningún Eureka, así que si el cliente estuviera activo llenaría el log de errores de conexión
 * y cada prueba esperaría a que vencieran sus reintentos. Además, en un equipo donde sí hubiera
 * un Eureka abierto, las pruebas se registrarían en él como si fueran el servicio de verdad.</p>
 *
 * <p>Cómo se comprueba: el cliente de Eureka es un bean que Spring solo crea cuando el cliente
 * está activado. Las pruebas lo desactivan en {@link EntornoDePruebas}; si alguien quita esa
 * propiedad, el bean aparece y esta prueba falla. Sin ese bean no existe nadie que pueda abrir
 * una conexión hacia Eureka.</p>
 *
 * <p>Necesita Docker abierto, como el resto de pruebas que arrancan el servicio completo.</p>
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
