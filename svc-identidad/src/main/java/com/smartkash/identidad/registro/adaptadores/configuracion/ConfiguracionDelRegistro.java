package com.smartkash.identidad.registro.adaptadores.configuracion;

import com.smartkash.identidad.registro.dominio.PoliticaContrasena;
import com.smartkash.identidad.registro.puertos.ContrasenasComunes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Prepara los objetos que el caso de uso del registro necesita y que Spring no sabe crear
 * por sí solo.
 *
 * <p>Qué es: una clase de configuración. Cada método marcado con {@code @Bean} le dice a
 * Spring "cuando alguien necesite un objeto de este tipo, créalo así". Hace falta para las
 * clases que no pueden llevar anotaciones de Spring: las del dominio, que son Java puro, y
 * las de Java, como el reloj.</p>
 *
 * <p>De dónde vienen los datos: la lista de contraseñas comunes la entrega Spring. A dónde
 * van: los objetos creados aquí llegan al constructor del caso de uso.</p>
 */
@Configuration
class ConfiguracionDelRegistro {

    /**
     * Crea las reglas de la contraseña con la lista de contraseñas comunes del servicio.
     *
     * @param contrasenasComunes la lista leída del archivo; la entrega Spring
     * @return las reglas que valida el registro
     */
    @Bean
    PoliticaContrasena politicaContrasena(ContrasenasComunes contrasenasComunes) {
        return new PoliticaContrasena(contrasenasComunes);
    }

    /**
     * Crea el reloj del que el servicio toma la fecha y la hora.
     *
     * <p>Es el reloj real, en hora universal (UTC), para que el momento guardado no dependa
     * de la zona horaria de la máquina. En las pruebas del caso de uso se sustituye por uno
     * detenido en un instante fijo.</p>
     *
     * @return el reloj del sistema en UTC
     */
    @Bean
    Clock reloj() {
        return Clock.systemUTC();
    }
}
