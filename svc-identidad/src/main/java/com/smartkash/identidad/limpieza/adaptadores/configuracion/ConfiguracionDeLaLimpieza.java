package com.smartkash.identidad.limpieza.adaptadores.configuracion;

import com.smartkash.identidad.limpieza.aplicacion.LimpiarDatosCaducados;
import com.smartkash.identidad.limpieza.puertos.AlmacenDeDatosCaducados;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

/**
 * Crea el caso de uso de la limpieza y activa las tareas programadas del servicio.
 *
 * <p>{@code @EnableScheduling} enciende en Spring el mecanismo que ejecuta los métodos con
 * {@code @Scheduled}. Sin él, la anotación se ignora en silencio y la limpieza no se
 * ejecutaría nunca; la prueba de la tarea programada lo vigila.</p>
 *
 * <p>Por qué el caso de uso se crea aquí: es Java puro y no sabe nada de Spring (lo comprueba
 * ArchUnit). Esta clase le entrega el almacén y el reloj del servicio.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class ConfiguracionDeLaLimpieza {

    /**
     * Crea el caso de uso.
     *
     * @param almacen el adaptador que borra en PostgreSQL
     * @param reloj   el reloj del servicio
     * @return el caso de uso
     */
    @Bean
    LimpiarDatosCaducados limpiarDatosCaducados(AlmacenDeDatosCaducados almacen, Clock reloj) {
        return new LimpiarDatosCaducados(almacen, reloj);
    }
}
