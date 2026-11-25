package com.smartkash.finanzas.cuentas.adaptadores.configuracion;

import com.smartkash.finanzas.cuentas.aplicacion.ConsultarCuentas;
import com.smartkash.finanzas.cuentas.puertos.RepositorioCuentas;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Crea los casos de uso de cuentas y los entrega a Spring.
 *
 * <p>Por qué existe: los casos de uso no llevan anotaciones de Spring, para seguir siendo Java puro.
 * Esta clase, que sí es de Spring, los construye con las piezas que necesitan.</p>
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracionDeCuentas {

    @Bean
    ConsultarCuentas consultarCuentas(RepositorioCuentas cuentas) {
        return new ConsultarCuentas(cuentas);
    }
}