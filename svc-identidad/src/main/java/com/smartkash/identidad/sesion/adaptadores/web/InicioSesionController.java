package com.smartkash.identidad.sesion.adaptadores.web;

import com.smartkash.identidad.sesion.aplicacion.IniciarSesion;
import com.smartkash.identidad.sesion.aplicacion.SesionIniciada;
import com.smartkash.identidad.sesion.dominio.TokenRenovacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Puerta de entrada web del inicio de sesión.
 *
 * <p>Qué hace: recibe correo y contraseña, llama al caso de uso y devuelve el token de acceso
 * en el cuerpo y el de renovación en una cookie. Los errores (credenciales incorrectas,
 * bloqueo) los convierte {@link ManejadorErroresDeInicioSesion}.</p>
 *
 * <p>De dónde vienen los datos: del cuerpo JSON de {@code POST /api/v1/auth/inicio-sesion},
 * una ruta pública, y de la dirección IP de la conexión. A dónde van: al caso de uso
 * {@link IniciarSesion}.</p>
 *
 * <p>La dirección IP: se toma de la conexión directa ({@code getRemoteAddr}). La cabecera
 * {@code X-Forwarded-For} la puede escribir cualquiera; si se usara, un atacante pondría una
 * IP distinta en cada intento y el límite por IP no le afectaría nunca. El servicio no
 * activa la lectura de cabeceras de proxy, así que {@code getRemoteAddr} es siempre quien se
 * conectó.</p>
 */
@RestController
@RequestMapping("/api/v1/auth")
class InicioSesionController {

    /** Nombre de la cookie con el token de renovación, el que fija el contrato. */
    static final String COOKIE_RENOVACION = "renovacion";

    /** La cookie solo viaja a las rutas de autenticación, no al resto de la API. */
    static final String RUTA_COOKIE = "/api/v1/auth";

    private final IniciarSesion iniciarSesion;

    /**
     * @param iniciarSesion el caso de uso; lo entrega Spring
     */
    InicioSesionController(IniciarSesion iniciarSesion) {
        this.iniciarSesion = iniciarSesion;
    }

    /**
     * Inicia la sesión de una persona.
     *
     * <p>La cookie lleva estos atributos de seguridad:</p>
     * <ul>
     *   <li>{@code HttpOnly}: el código de la página no puede leerla; un script malicioso no
     *       puede robarla.</li>
     *   <li>{@code Secure}: solo viaja por HTTPS (los navegadores lo admiten también en
     *       {@code localhost}).</li>
     *   <li>{@code SameSite=Strict}: el navegador no la envía en peticiones que salen de otro
     *       sitio web.</li>
     *   <li>{@code Path=/api/v1/auth} y 7 días de duración, lo mismo que el token.</li>
     * </ul>
     *
     * <p>{@code Cache-Control: no-store} impide que la respuesta, que contiene un token, quede
     * guardada en una caché.</p>
     *
     * @param solicitud correo y contraseña, ya validados en su forma
     * @param peticion  la petición HTTP, de la que se toma la dirección IP
     * @return 200 con la sesión y la cookie
     */
    @PostMapping("/inicio-sesion")
    ResponseEntity<SesionRespuesta> iniciar(
            @Valid @RequestBody InicioSesionSolicitud solicitud, HttpServletRequest peticion) {

        SesionIniciada sesion = iniciarSesion.ejecutar(
                solicitud.correo(), solicitud.contrasena(), peticion.getRemoteAddr());

        ResponseCookie cookie = ResponseCookie.from(COOKIE_RENOVACION, sesion.tokenRenovacion())
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(RUTA_COOKIE)
                .maxAge(TokenRenovacion.VIGENCIA)
                .build();

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie.toString())
                .cacheControl(CacheControl.noStore())
                .body(SesionRespuesta.desde(sesion));
    }
}