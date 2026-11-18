package com.smartkash.eureka;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Seguridad del servidor de descubrimiento.
 *
 * <p>Eureka es el registro donde cada servicio publica su nombre y su dirección, y donde los
 * demás la consultan. Si cualquiera pudiera escribir en ese registro, un atacante inscribiría un
 * servicio falso con el nombre de uno real y recibiría las peticiones que van a este. Por eso
 * toda ruta exige credenciales.
 *
 * <p>La autenticación es básica: el usuario y la contraseña viajan en la cabecera
 * {@code Authorization} de cada petición. Es adecuada porque Eureka solo escucha en la máquina
 * local o en una red privada, nunca expuesta a internet.
 *
 * <p>La protección CSRF se desactiva. Está pensada para navegadores que conservan una sesión en
 * una cookie, y aquí no hay ni sesiones ni cookies: los servicios se registran con peticiones
 * {@code POST} que llevan sus credenciales en cada llamada. Dejarla activa haría que Eureka
 * rechazara esos registros.
 */
// @Configuration: esta clase no contiene lógica de negocio, sino piezas que Spring debe
// construir al arrancar. Spring la lee una vez, ejecuta sus métodos marcados con @Bean y guarda
// los objetos resultantes para entregarlos a quien los necesite.

// @EnableWebSecurity: activa el soporte de seguridad web de Spring Security y declara que esta
// clase define las reglas. Spring Boot ya lo activa solo cuando detecta la dependencia, así que
// aquí es redundante, pero se deja explícito para que quien lea la clase vea de dónde sale la
// seguridad.

@Configuration
@EnableWebSecurity
public class SeguridadEureka {
    // @Bean: el objeto que devuelve este metodo lo crea y lo guarda Spring, no el código a mano.
    // Spring Security busca entre sus piezas una de tipo SecurityFilterChain y la usa para decidir
    // quién puede pasar. Es el punto de enganche con el que se sustituyen las reglas por defecto.
    @Bean
    public SecurityFilterChain cadenaDeSeguridad(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(rutas -> rutas.anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());

        return http.build();
    }
}
