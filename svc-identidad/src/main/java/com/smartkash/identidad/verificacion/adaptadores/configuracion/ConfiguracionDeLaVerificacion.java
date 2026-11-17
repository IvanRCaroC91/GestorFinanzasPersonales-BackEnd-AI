package com.smartkash.identidad.verificacion.adaptadores.configuracion;

import com.smartkash.identidad.cuenta.puertos.RepositorioUsuarios;
import com.smartkash.identidad.tokens.puertos.CalculadorHuellas;
import com.smartkash.identidad.tokens.puertos.GeneradorTokens;
import com.smartkash.identidad.verificacion.aplicacion.EmitirEnlaceDeVerificacion;
import com.smartkash.identidad.verificacion.aplicacion.ReenviarVerificacion;
import com.smartkash.identidad.verificacion.aplicacion.VerificarCorreo;
import com.smartkash.identidad.verificacion.puertos.EnviadorCorreos;
import com.smartkash.identidad.verificacion.puertos.RepositorioTokensDeUnUso;
import com.smartkash.identidad.verificacion.puertos.UnidadDeTrabajo;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Crea los casos de uso de la verificación del correo y les entrega sus adaptadores.
 *
 * <p>Por qué hace falta: los casos de uso no llevan anotaciones de Spring, así que Spring no
 * los encontraría solo. Cada parámetro lo entrega Spring: son los adaptadores reales que
 * implementan los puertos.</p>
 *
 * <p>Regla: el servicio no arranca sin la dirección base de los enlaces, o si no es una
 * dirección web. Sin ella, los correos llevarían enlaces que no funcionan.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PropiedadesDeLaVerificacion.class)
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

    /**
     * Crea el caso de uso que emite y envía enlaces de verificación.
     *
     * @param tokens          repositorio de tokens de verificación
     * @param generador       generador de valores aleatorios
     * @param huellas         calculador de huellas
     * @param enviador        enviador de correos
     * @param unidadDeTrabajo transacciones
     * @param reloj           reloj del servicio, en UTC
     * @param propiedades     configuración, de la que se usa la dirección base
     * @return el caso de uso listo para usarse
     * @throws IllegalStateException si falta la dirección base o no es una dirección web
     */
    @Bean
    EmitirEnlaceDeVerificacion emitirEnlaceDeVerificacion(
            RepositorioTokensDeUnUso tokens,
            GeneradorTokens generador,
            CalculadorHuellas huellas,
            EnviadorCorreos enviador,
            UnidadDeTrabajo unidadDeTrabajo,
            Clock reloj,
            PropiedadesDeLaVerificacion propiedades) {
        String base = propiedades.direccionBase();
        if (base == null || !(base.startsWith("https://") || base.startsWith("http://"))) {
            throw new IllegalStateException("Falta la dirección de la web para los enlaces de "
                    + "verificación: define la variable de entorno IDENTIDAD_ENLACE_BASE con una "
                    + "dirección que empiece por http:// o https://.");
        }
        return new EmitirEnlaceDeVerificacion(tokens, generador, huellas, enviador, unidadDeTrabajo, reloj, base);
    }

    /**
     * Crea el caso de uso que reenvía el enlace de verificación.
     *
     * @param usuarios     repositorio de cuentas
     * @param tokens       repositorio de tokens de verificación
     * @param emitirEnlace caso de uso que emite el enlace
     * @param reloj        reloj del servicio, en UTC
     * @return el caso de uso listo para usarse
     */
    @Bean
    ReenviarVerificacion reenviarVerificacion(
            RepositorioUsuarios usuarios,
            RepositorioTokensDeUnUso tokens,
            EmitirEnlaceDeVerificacion emitirEnlace,
            Clock reloj) {
        return new ReenviarVerificacion(usuarios, tokens, emitirEnlace, reloj);
    }
}