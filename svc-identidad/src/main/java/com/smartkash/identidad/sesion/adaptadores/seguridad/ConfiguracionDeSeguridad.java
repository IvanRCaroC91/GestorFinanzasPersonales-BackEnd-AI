package com.smartkash.identidad.sesion.adaptadores.seguridad;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.session.DisableEncodeUrlFilter;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher.pathPattern;

/**
 * Define qué rutas son públicas, cómo se comprueba el token de acceso y qué se responde
 * cuando falta o no vale.
 *
 * <p>Qué es la cadena de filtros: antes de que una petición llegue a un controlador, Spring
 * Security la hace pasar por una serie de filtros. Uno lee la cabecera
 * {@code Authorization: Bearer <token>}, otro comprueba el token y otro decide si la ruta
 * exige autenticación. Si algo falla, la petición no llega al controlador.</p>
 *
 * <p>Decisiones:</p>
 * <ul>
 *   <li><b>Rutas públicas:</b> registro, inicio de sesión, verificación del correo y claves
 *       públicas, cada una solo con su método. Todo lo demás exige token.</li>
 *   <li><b>En las rutas públicas se ignora la cabecera de autorización.</b> Si no, un token
 *       caducado que el navegador siga enviando impediría volver a iniciar sesión.</li>
 *   <li><b>Sin sesión de servidor:</b> cada petición se valida sola con su token; el servidor
 *       no recuerda nada entre peticiones ni crea la cookie {@code JSESSIONID}.</li>
 *   <li><b>Sin protección CSRF:</b> esa protección evita que otra web use la sesión que el
 *       navegador guarda en cookies. Aquí el token de acceso viaja en una cabecera que el
 *       navegador nunca envía por su cuenta. La cookie de renovación, la única cookie, va
 *       con {@code SameSite=Strict}, que impide que otra web la envíe.</li>
 *   <li><b>Sin formulario de inicio de sesión ni autenticación básica:</b> el único modo de
 *       autenticarse es el token.</li>
 *   <li><b>Tope de tamaño del cuerpo:</b> el primer filtro de la cadena rechaza los cuerpos de
 *       más de {@link #TAMANO_MAXIMO_DEL_CUERPO} bytes (ver {@link TopeDelCuerpo}).</li>
 * </ul>
 *
 * <p>{@code @EnableWebSecurity} activa Spring Security con esta configuración. Spring Boot ya
 * lo haría solo; se declara para que la clase funcione también en pruebas que no arrancan
 * Spring Boot.</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
class ConfiguracionDeSeguridad {

    /** Rutas a las que se puede llamar sin token, cada una con su método. */
    static final RequestMatcher RUTAS_PUBLICAS = new OrRequestMatcher(
            pathPattern(HttpMethod.POST, "/api/v1/auth/registro"),
            pathPattern(HttpMethod.POST, "/api/v1/auth/inicio-sesion"),
            pathPattern(HttpMethod.POST, "/api/v1/auth/verificacion-correo"),
            pathPattern(HttpMethod.GET, "/.well-known/jwks.json"));

    /**
     * Mayor tamaño de cuerpo admitido: 16 KiB. El mayor cuerpo válido del servicio (el del
     * registro, con todos los campos al máximo) mide menos de 2 KiB; el margen evita rechazar
     * por error cuerpos válidos con caracteres que ocupan varios bytes.
     */
    static final int TAMANO_MAXIMO_DEL_CUERPO = 16 * 1024;

    /**
     * Crea el validador de tokens de acceso.
     *
     * <p>Comprueba, en este orden: que el algoritmo sea RS256 (cualquier otro, incluido
     * {@code none}, se rechaza), la firma con la clave pública del servicio, que no esté
     * caducado (con un margen de 60 segundos por si los relojes difieren), que el emisor sea
     * el configurado y que el destinatario incluya {@value EmisorJwtRs256#DESTINATARIO}.
     * Valida en memoria, sin llamar a ningún otro servicio.</p>
     *
     * @param clave       la clave de firma del servicio, de la que se usa la parte pública
     * @param propiedades la configuración de los tokens, de la que se usa el emisor
     * @return el validador
     */
    @Bean
    JwtDecoder validadorDeTokens(ClaveDeFirma clave, PropiedadesDelToken propiedades) {
        NimbusJwtDecoder validador = NimbusJwtDecoder.withPublicKey(clave.clavePublica())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        validador.setJwtValidator(JwtValidators.createDefaultWithValidators(
                new JwtIssuerValidator(propiedades.emisor()),
                new JwtAudienceValidator(EmisorJwtRs256.DESTINATARIO)));
        return validador;
    }

    /**
     * Arma la cadena de filtros.
     *
     * @param http       el constructor de la cadena que entrega Spring Security
     * @param validador  el validador de tokens
     * @param resolutor  el mecanismo de Spring MVC que busca el manejador de cada error; se
     *                   pide por su nombre porque hay más de uno de ese tipo
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
                // Lo primero: rechazar cuerpos demasiado grandes, antes incluso de mirar el token.
                .addFilterBefore(new TopeDelCuerpo(TAMANO_MAXIMO_DEL_CUERPO, resolutor), DisableEncodeUrlFilter.class)
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sin esto, Spring guardaría en una sesión la petición rechazada para repetirla
                // tras iniciar sesión, algo que solo tiene sentido con formularios.
                .requestCache(cache -> cache.requestCache(new NullRequestCache()))
                .authorizeHttpRequests(rutas -> rutas
                        .requestMatchers(RUTAS_PUBLICAS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(servidor -> servidor
                        .bearerTokenResolver(lectorDeTokenFueraDeRutasPublicas())
                        .authenticationEntryPoint(puntoDeEntrada)
                        .jwt(jwt -> jwt
                                .decoder(validador)
                                .jwtAuthenticationConverter(conversorDeRoles())))
                .exceptionHandling(errores -> errores.authenticationEntryPoint(puntoDeEntrada));

        return http.build();
    }

    /*
     * Lee el token de la cabecera Authorization, salvo en las rutas públicas, donde hace como
     * si no hubiera cabecera. El lector estándar tampoco acepta el token en la dirección web
     * ni en formularios, que es lo seguro.
     */
    private static BearerTokenResolver lectorDeTokenFueraDeRutasPublicas() {
        DefaultBearerTokenResolver estandar = new DefaultBearerTokenResolver();
        return peticion -> RUTAS_PUBLICAS.matches(peticion) ? null : estandar.resolve(peticion);
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