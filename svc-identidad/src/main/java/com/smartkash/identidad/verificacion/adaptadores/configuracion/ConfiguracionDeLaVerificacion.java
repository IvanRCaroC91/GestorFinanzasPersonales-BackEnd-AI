package com.smartkash.identidad.verificacion.adaptadores.configuracion;

import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.tokens.puertos.CalculadorHuellas;
import com.smartkash.identidad.verificacion.aplicacion.VerificarCorreo;
import com.smartkash.identidad.verificacion.puertos.RepositorioTokensDeUnUso;
import com.smartkash.identidad.verificacion.puertos.UnidadDeTrabajo;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Crea los casos de uso de la verificación del correo y les entrega sus adaptadores.
 *
 * <p>Por qué hace falta: los casos de uso no llevan anotaciones de Spring, así que Spring no
 * los encontraría solo. Cada parámetro lo entrega Spring: son los adaptadores reales que
 * implementan los puertos.</p>
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracionDeLaVerificacion {

    /**
     * Crea el caso de uso que verifica el correo.
     *
     * @param tokens          repositorio de tokens de verificación
     * @param usuarios        repositorio de cuentas
     * @param huellas         calculador de huellas
     * @param unidadDeTrabajo transacciones
     * @param reloj           reloj del servicio, en UTC
     * @return el caso de uso listo para usarse
     */
    @Bean
    VerificarCorreo verificarCorreo(
            RepositorioTokensDeUnUso tokens,
            RepositorioUsuarios usuarios,
            CalculadorHuellas huellas,
            UnidadDeTrabajo unidadDeTrabajo,
            Clock reloj) {
        return new VerificarCorreo(tokens, usuarios, huellas, unidadDeTrabajo, reloj);
    }
}
