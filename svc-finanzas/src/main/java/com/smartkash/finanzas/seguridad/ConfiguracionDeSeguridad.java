package com.smartkash.finanzas.seguridad;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.time.Duration;

/**
 * Define cómo se comprueba el token de acceso y qué se responde cuando falta o no vale.
 *
 * <p>Qué es la cadena de filtros: antes de que una petición llegue a un controlador, Spring
 * Security la hace pasar por una serie de filtros. Uno lee la cabecera
 * {@code Authorization: Bearer <token>}, otro comprueba el token y otro decide si la ruta
 * exige autenticación. Si algo falla, la petición no llega al controlador.</p>
 *
 * <p>Decisiones:</p>
 * <ul>
 *   <li><b>Toda ruta exige token.</b> Este servicio no tiene rutas públicas.</li>
 *   <li><b>Sin sesión de servidor:</b> cada petición se valida sola con su token; el servidor
 *       no recuerda nada entre peticiones ni crea la cookie {@code JSESSIONID}.</li>
 *   <li><b>Sin protección CSRF:</b> esa protección evita que otra web use la sesión que el
 *       navegador guarda en cookies. Aquí no hay cookies: el token viaja en una cabecera que el
 *       navegador nunca envía por su cuenta.</li>
 *   <li><b>Sin formulario de inicio de sesión ni autenticación básica:</b> el único modo de
 *       autenticarse es el token, que emite el servicio de identidad.</li>
 * </ul>
 *
 * <p>{@code @EnableWebSecurity} activa Spring Security con esta configuración. Spring Boot ya
 * lo haría solo; se declara para que la clase funcione también en pruebas que no arrancan
 * Spring Boot.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
@EnableConfigurationProperties(PropiedadesDeSeguridad.class)
class ConfiguracionDeSeguridad {

    /** Destinatario que debe figurar en todo token: el mismo que pone el servicio de identidad. */
    static final String DESTINATARIO = "api";

    /** Diferencia de reloj que se tolera al comprobar la caducidad de un token. */
    static final Duration MARGEN_DE_RELOJES = Duration.ofSeconds(60);

    /**
     * Crea el validador de tokens de acceso.
     *
     * <p>Descarga las claves públicas del servicio de identidad la primera vez que llega un
     * token y las conserva en memoria. Si identidad no está disponible al arrancar, este
     * servicio arranca igualmente.</p>
     *
     * @param propiedades la configuración, de la que se usan la dirección de las claves y el
     *                    emisor
     * @return el validador
     */
    @Bean
    JwtDecoder validadorDeTokens(PropiedadesDeSeguridad propiedades) {
        NimbusJwtDecoder validador = NimbusJwtDecoder.withJwkSetUri(propiedades.jwksUri())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        return conValidaciones(validador, propiedades.emisor());
    }

    /**
     * Añade a un validador las reglas del servicio.
     *
     * <p>Comprueba, además de la firma con algoritmo RS256 (cualquier otro, incluido
     * {@code none}, se rechaza): que tenga fecha de caducidad y no esté caducado (con un margen de
     * 60 segundos por si los relojes difieren), que el emisor sea el configurado y que el destinatario incluya
     * {@value #DESTINATARIO}. Está separado para poder probar estas reglas con una clave local,
     * sin descargar nada.</p>
     *
     * @param validador el validador al que se añaden las reglas
     * @param emisor    emisor que debe figurar en el token
     * @return el mismo validador, ya con las reglas
     */
    static NimbusJwtDecoder conValidaciones(NimbusJwtDecoder validador, String emisor) {
        JwtTimestampValidator caducidad = new JwtTimestampValidator(MARGEN_DE_RELOJES);
        caducidad.setAllowEmptyExpiryClaim(false);
        validador.setJwtValidator(JwtValidators.createDefaultWithValidators(
                caducidad,
                new JwtIssuerValidator(emisor),
                new JwtAudienceValidator(DESTINATARIO)));
        return validador;
    }

    /**
     * Arma la cadena de filtros.
     *
     * @param http      el constructor de la cadena que entrega Spring Security
     * @param validador el validador de tokens
     * @param resolutor el mecanismo de Spring MVC que busca el manejador de cada error; se
     *                  pide por su nombre porque hay más de uno de ese tipo
     * @return la cadena de filtros
     * @throws Exception si la configuración no es válida
     */
    @Bean
    SecurityFilterChain cadenaDeFiltros(
            HttpSecurity http,
            JwtDecoder validador,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolutor) throws Exception {

        // Los errores de los filtros se reenvían a Spring MVC para que los atienda
        // ManejadorErroresDeAutenticacion, con el mismo formato que el resto de errores.
        AuthenticationEntryPoint puntoDeEntrada =
                (peticion, respuesta, error) -> resolutor.resolveException(peticion, respuesta, null, error);

        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sin esto, Spring guardaría en una sesión la petición rechazada para repetirla
                // tras iniciar sesión, algo que solo tiene sentido con formularios.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(rutas -> rutas.anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .authenticationEntryPoint(puntoDeEntrada)
                        .jwt(jwt -> jwt
                                .decoder(validador)
                                .jwtAuthenticationConverter(conversorDeRoles())))
                .exceptionHandling(errores -> errores.authenticationEntryPoint(puntoDeEntrada));

        return http.build();
    }

    /*
     * Convierte el dato "roles" del token en permisos de Spring Security con el prefijo ROLE_
     * (USUARIO pasa a ser ROLE_USUARIO), que es la forma que esperan las reglas por rol.
     */
    private static JwtAuthenticationConverter conversorDeRoles() {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(roles);
        return conversor;
    }
}