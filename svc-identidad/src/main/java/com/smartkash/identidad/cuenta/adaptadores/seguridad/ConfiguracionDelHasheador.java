package com.smartkash.identidad.cuenta.adaptadores.seguridad;

import com.smartkash.identidad.cuenta.puertos.HasheadorContrasena;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Registra en Spring el hasheador de contraseñas del servicio: Argon2 envuelto en el tope de
 * cálculos simultáneos.
 *
 * <p>Qué es un bean: un objeto que Spring crea una vez y entrega a quien lo pida. Por ejemplo,
 * el registro y el inicio de sesión piden un {@link HasheadorContrasena} y los dos reciben este
 * mismo objeto, con el mismo semáforo. Por eso el tope es común a los dos: un ataque contra el
 * inicio de sesión no puede sumar sus cálculos a los del registro para saltárselo.</p>
 *
 * <p>Por qué una clase de configuración y no {@code @Component} en {@link HasheadorArgon2}:
 * hay dos objetos (el Argon2 y su envoltorio) y solo el envoltorio debe ser un bean. Si Argon2
 * también lo fuera, alguien podría pedirlo directamente y saltarse el tope.</p>
 */
@Configuration(proxyBeanMethods = false)
class ConfiguracionDelHasheador {

    /**
     * Tiempo máximo que un cálculo espera turno. Un cálculo dura decenas de milisegundos, así
     * que 2 segundos solo se agotan con el servicio desbordado; es poco para que la persona
     * note la espera y suficiente para absorber picos cortos.
     */
    static final Duration ESPERA_MAXIMA = Duration.ofSeconds(2);

    /**
     * Crea el hasheador del servicio.
     *
     * <p>Turnos: uno por núcleo del procesador. Argon2 está configurado con un hilo por
     * cálculo, así que más turnos que núcleos no harían ir más rápido: solo repartirían los
     * núcleos entre más cálculos y gastarían más memoria.</p>
     *
     * @return el hasheador Argon2 con tope
     */
    @Bean
    HasheadorContrasena hasheadorContrasena() {
        int nucleos = Runtime.getRuntime().availableProcessors();
        return new HasheadorConTope(new HasheadorArgon2(), nucleos, ESPERA_MAXIMA);
    }
}
